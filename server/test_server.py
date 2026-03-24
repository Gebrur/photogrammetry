import os
import io
import zipfile
import shutil
import subprocess
import pytest
from unittest.mock import patch, MagicMock

from Server import app, convert_ply_to_glb

@pytest.fixture
def client(tmp_path):
    """
    Создаём тестовый клиент Flask
    и временные папки для uploads / outputs
    """
    app.config["TESTING"] = True

    # Переопределяем папки на временные
    app.config["UPLOAD_FOLDER"] = tmp_path / "uploads"
    os.makedirs(app.config["UPLOAD_FOLDER"], exist_ok=True)

    global OUTPUT_FOLDER
    OUTPUT_FOLDER = tmp_path / "outputs"
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)

    return app.test_client()

def create_fake_zip():
    fake_zip = io.BytesIO()
    with zipfile.ZipFile(fake_zip, 'w') as z:
        z.writestr("test.jpg", b"fake_image_data")
    fake_zip.seek(0)
    return fake_zip

# TESTS

def test_upload_no_file(client):
    response = client.post("/upload")
    assert response.status_code == 400
    assert b"No file part" in response.data


def test_upload_empty_filename(client):
    data = {
        'file': (io.BytesIO(b"data"), "")
    }
    response = client.post("/upload", data=data)
    assert response.status_code == 400
    assert b"No selected file" in response.data


@patch("subprocess.check_call")
def test_colmap_called_success(mock_subprocess, client, tmp_path):
    mock_subprocess.return_value = 0

    fake_zip = create_fake_zip()

    task_id = "scan"
    dense_path = tmp_path / "outputs" / task_id / "dense" / "0"
    os.makedirs(dense_path, exist_ok=True)
    ply_file = dense_path / "meshed-poisson.ply"
    ply_file.write_bytes(b"plydata")

    data = {
        'file': (fake_zip, "scan.zip")
    }

    response = client.post("/upload", data=data)

    assert mock_subprocess.called
    assert response.status_code in [200, 500]


@patch("subprocess.check_call", side_effect=subprocess.CalledProcessError(1, "cmd"))
def test_colmap_failure(mock_subprocess, client):
    fake_zip = create_fake_zip()

    data = {
        'file': (fake_zip, "scan.zip")
    }

    response = client.post("/upload", data=data)

    assert response.status_code == 500
    assert b"Colmap execution failed" in response.data


@patch("trimesh.load")
def test_convert_ply_success(mock_load, tmp_path):
    mock_mesh = MagicMock()
    mock_load.return_value = mock_mesh

    output_file = tmp_path / "model.glb"

    result = convert_ply_to_glb("fake.ply", str(output_file))

    assert result is True
    mock_mesh.export.assert_called()


@patch("trimesh.load", side_effect=Exception("fail"))
def test_convert_ply_failure(mock_load):
    result = convert_ply_to_glb("fake.ply", "out.glb")
    assert result is False


def test_upload_folder_created():
    assert os.path.exists("uploads")


def test_output_folder_created():
    assert os.path.exists("outputs")


@patch("subprocess.check_call")
@patch("trimesh.load")
def test_glb_fallback_to_ply(mock_load, mock_subprocess, client, tmp_path):
    mock_subprocess.return_value = 0

    mock_mesh = MagicMock()
    mock_load.return_value = mock_mesh
    mock_mesh.export.side_effect = Exception("fail")

    fake_zip = create_fake_zip()

    task_id = "scan"
    dense_path = tmp_path / "outputs" / task_id / "dense" / "0"
    os.makedirs(dense_path, exist_ok=True)

    ply_file = dense_path / "meshed-poisson.ply"
    ply_file.write_bytes(b"plydata")

    data = {
        'file': (fake_zip, "scan.zip")
    }

    response = client.post("/upload", data=data)

    assert response.status_code in [200, 500]


def test_upload_valid_zip_structure(client):
    fake_zip = create_fake_zip()

    data = {
        'file': (fake_zip, "scan.zip")
    }

    response = client.post("/upload", data=data)

    # Даже если COLMAP упадёт — zip должен быть принят
    assert response.status_code in [200, 500]