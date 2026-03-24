import os
import subprocess
import zipfile
import shutil
import trimesh  # <--- НОВАЯ БИБЛИОТЕКА
from flask import Flask, request, send_file, jsonify
from werkzeug.utils import secure_filename

app = Flask(__name__)

# --- КОНФИГУРАЦИЯ ---
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_FOLDER = os.path.join(BASE_DIR, 'uploads')
OUTPUT_FOLDER = os.path.join(BASE_DIR, 'outputs')

# Имя батника (Colmap должен быть в PATH)
COLMAP_BIN = r"D:\colmap\COLMAP.bat"

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

# Создаем папки
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(OUTPUT_FOLDER, exist_ok=True)


def convert_ply_to_glb(ply_path, output_path):
    """
    Конвертирует .ply модель в .glb используя библиотеку trimesh.
    """
    print(f"Начинаю конвертацию PLY -> GLB: {ply_path}")
    try:
        # Загружаем меш
        mesh = trimesh.load(ply_path)

        # Если trimesh загрузил это как "Scene" (бывает с некоторыми PLY),
        # берем первую геометрию, или экспортируем как есть.

        # Экспорт в GLB (binary glTF)
        # mesh.export возвращает байты, если не указать file_obj,
        # но мы укажем путь для сохранения.
        mesh.export(output_path)

        print(f"Конвертация успешна! Сохранено в: {output_path}")
        return True
    except Exception as e:
        print(f"Ошибка при конвертации: {e}")
        return False


def run_colmap_pipeline(task_id):
    """
    Запускает автоматический пайплайн реконструкции.
    """
    work_dir = os.path.join(UPLOAD_FOLDER, task_id)
    images_dir = os.path.join(work_dir, "images")
    workspace_path = os.path.join(OUTPUT_FOLDER, task_id)
    os.makedirs(workspace_path, exist_ok=True)

    cmd = [
        "cmd.exe", "/c", COLMAP_BIN, "automatic_reconstructor",
        "--workspace_path", workspace_path,
        "--image_path", images_dir,
        "--quality", "low",
        "--data_type", "individual",
        "--single_camera", "1",
        "--dense", "1"
    ]

    print(f"[{task_id}] Запуск COLMAP pipeline...")
    try:
        subprocess.check_call(cmd)
        print(f"[{task_id}] COLMAP завершил работу успешно!")
        return True
    except subprocess.CalledProcessError as e:
        print(f"[{task_id}] Ошибка при выполнении COLMAP: {e}")
        return False
    except FileNotFoundError:
        print(f"ERROR: Не найден файл {COLMAP_BIN}.")
        return False


@app.route('/upload', methods=['POST'])
def upload_file():
    if 'file' not in request.files:
        return jsonify({'error': 'No file part'}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    filename = secure_filename(file.filename)
    task_id = os.path.splitext(filename)[0]

    save_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    file.save(save_path)
    print(f"[{task_id}] Файл получен.")

    # Распаковка
    extract_path = os.path.join(app.config['UPLOAD_FOLDER'], task_id)
    if os.path.exists(extract_path):
        shutil.rmtree(extract_path)
    os.makedirs(extract_path)

    with zipfile.ZipFile(save_path, 'r') as zip_ref:
        zip_ref.extractall(extract_path)

    # Нормализация структуры папок
    images_dir = os.path.join(extract_path, "images")
    if not os.path.exists(images_dir):
        os.makedirs(images_dir)
        for item in os.listdir(extract_path):
            src_path = os.path.join(extract_path, item)
            if os.path.isfile(src_path) and item.lower().endswith(('.jpg', '.png', '.jpeg')):
                shutil.move(src_path, os.path.join(images_dir, item))

    # Запуск COLMAP
    success = run_colmap_pipeline(task_id)

    if success:
        # Ищем PLY файл
        ply_path = os.path.join(OUTPUT_FOLDER, task_id, "dense", "0", "meshed-poisson.ply")
        if not os.path.exists(ply_path):
            ply_path = os.path.join(OUTPUT_FOLDER, task_id, "dense", "0", "fused.ply")

        if os.path.exists(ply_path):
            # --- НОВЫЙ БЛОК КОНВЕРТАЦИИ ---
            glb_filename = "model.glb"
            glb_path = os.path.join(OUTPUT_FOLDER, task_id, "dense", "0", glb_filename)

            convert_success = convert_ply_to_glb(ply_path, glb_path)

            if convert_success and os.path.exists(glb_path):
                print(f"[{task_id}] Отправка GLB клиенту...")
                return send_file(glb_path, as_attachment=True, download_name="model.glb")
            else:
                # Если конвертация упала, отдаем PLY как запасной вариант
                print(f"[{task_id}] Конвертация не удалась, отправляю PLY.")
                return send_file(ply_path, as_attachment=True, download_name="model.ply")
            # ------------------------------
        else:
            return jsonify({'error': 'Mesh creation failed: Output file not found'}), 500
    else:
        return jsonify({'error': 'Colmap execution failed'}), 500


if __name__ == '__main__':
    print("Сервер запущен. Ожидание соединений...")
    app.run(host='0.0.0.0', port=5000, debug=True)


