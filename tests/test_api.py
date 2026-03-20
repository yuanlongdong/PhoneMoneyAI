from fastapi.testclient import TestClient

from app.main import app
from app.models import ActionType, TaskStatus

client = TestClient(app)


def test_health() -> None:
    response = client.get('/health')
    assert response.status_code == 200
    assert response.json() == {'status': 'ok'}


def test_plan_and_create_task_flow() -> None:
    plan_response = client.post('/plan', json={'goal': '打开微信并点击收款码', 'app_name': 'com.tencent.mm'})
    assert plan_response.status_code == 200
    plan_payload = plan_response.json()['dsl']
    assert plan_payload['intent'] == 'payment'
    assert plan_payload['steps'][0]['action'] == 'open_app'

    task_response = client.post('/task', json={'goal': '打开微信并点击收款码', 'app_name': 'com.tencent.mm'})
    assert task_response.status_code == 200
    task_payload = task_response.json()
    assert task_payload['goal'] == '打开微信并点击收款码'
    assert task_payload['status'] == TaskStatus.PENDING.value


def test_decide_and_validate_flow() -> None:
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
            'screen_width': 1080,
            'screen_height': 1920,
        },
    )
    assert decide_response.status_code == 200
    payload = decide_response.json()
    assert payload['action']['action'] == ActionType.TAP.value
    assert payload['action']['coordinates'] == [50, 50]
    assert payload['candidates'][0]['reasons']

    validate_response = client.post(
        '/validate',
        json={
            'state': {
                'goal': '收款码',
                'current_step': {
                    'id': 'step-2',
                    'description': 'Find target UI element',
                    'action': 'tap',
                    'target': '收款码',
                    'params': {},
                },
                'ui_tree': [],
                'ocr': [],
                'history': [],
                'last_action': None,
                'last_result': None,
                'screen_width': 100,
                'screen_height': 100,
            },
            'action': {'action': 'tap', 'coordinates': [999, 999]},
        },
    )
    assert validate_response.status_code == 200
    assert validate_response.json()['valid'] is False


def test_task_progress_feedback_and_execute_dry_run() -> None:
    task_response = client.post('/task', json={'goal': '打开微信并点击收款码', 'app_name': 'com.tencent.mm'})
    task_id = task_response.json()['task_id']

    next_response = client.get(f'/task/{task_id}/next')
    assert next_response.status_code == 200
    assert next_response.json()['state']['current_step']['id'] == 'step-1'

    result_response = client.post(f'/task/{task_id}/result', json={'success': False, 'error_type': 'not_found', 'message': 'button missing'})
    assert result_response.status_code == 200
    assert result_response.json()['status'] == TaskStatus.RETRY.value

    feedback_response = client.post(
        '/feedback',
        json={'task_id': task_id, 'step_id': 'step-1', 'action': 'tap', 'result': 'retrying'},
    )
    assert feedback_response.status_code == 200
    assert feedback_response.json()['result'] == 'retrying'

    execute_response = client.post(
        '/execute',
        json={
            'device_id': 'emulator-5554',
            'dry_run': True,
            'action': {'action': 'back'},
        },
    )
    assert execute_response.status_code == 200
    assert 'keyevent 4' in execute_response.json()['command']
