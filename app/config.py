from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "PhoneMoneyAI"
    openai_model: str = "gpt-4.1-mini"
    openai_api_key: str | None = None
    database_url: str = "sqlite:///./phonemoneyai.db"
    retry_limit: int = 3
    ocr_threshold: float = 0.6
    ui_priority: float = 0.75
    action_timeout_seconds: int = 15
    adb_path: str = Field(default="adb")

    model_config = SettingsConfigDict(env_prefix="PHONEMONEYAI_", env_file=".env", extra="ignore")


settings = Settings()
