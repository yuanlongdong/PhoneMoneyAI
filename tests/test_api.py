from fastapi.testclient import TestClient

from app.main import app
from app.models import ActionType

client = TestClient(app)


def test_health() -> None:
    response = client.get('/health')
    assert response.status_code == 200
    assert response.json() == {'status': 'ok'}


def test_create_task_and_decide_flow() -> None:
    task_response = client.post('/task', json={'goal': '打开微信并点击收款码', 'app_name': 'com.tencent.mm'})
    assert task_response.status_code == 200
    task_payload = task_response.json()
    assert task_payload['goal'] == '打开微信并点击收款码'

    decide_response = client.post(
        '/decide',
        json={
            'goal': '收款码',
            'current_step': {
                'id': 'step-2',
                'description': 'Find target UI element',
                'action': 'tap',
                'target': '收款码',
                'params': {},
            },
            'ui_tree': [
                {
                    'text': '收款码',
                    'resourceId': 'btn_payee_code',
                    'bounds': [0, 0, 100, 100],
                    'clickable': True,
                    'className': 'android.widget.Button',
                }
            ],
            'ocr': [],
            'history': [],
            'last_action': None,
            'last_result': None,
        },
    )
    assert decide_response.status_code == 200
    payload = decide_response.json()
    assert payload['action']['action'] == ActionType.TAP.value
    assert payload['action']['coordinates'] == [50, 50]


def test_execute_dry_run() -> None:
    response = client.post(
        '/execute',
        json={
            'device_id': 'emulator-5554',
            'dry_run': True,
            'action': {'action': 'back'},
        },
    )
    assert response.status_code == 200
    assert 'keyevent 4' in response.json()['command']
