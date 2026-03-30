from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    llm_provider: str = "openai"
    llm_model: str = "gpt-4o"
    llm_api_key: str = ""
    llm_base_url: str | None = None
    workspace_path: str = "/workspace"
    max_search_results: int = 50
    max_file_lines: int = 300

    model_config = {"env_prefix": "CQA_", "env_file": ".env"}


settings = Settings()
