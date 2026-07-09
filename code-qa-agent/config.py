from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    llm_provider: str = "openai"
    llm_model: str = "gpt-4o"
    llm_api_key: str = ""
    llm_base_url: str | None = None
    workspace_path: str = "/workspace"
    max_search_results: int = 50
    max_file_lines: int = 300
    auth_password: str = ""

    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "code_qa_agent"
    db_user: str = "code_qa_agent"
    db_password: str = "code_qa_agent"

    @property
    def database_url(self) -> str:
        return (
            f"postgresql+asyncpg://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    @property
    def database_sync_url(self) -> str:
        return (
            f"postgresql+psycopg2://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    model_config = {"env_prefix": "CQA_", "env_file": ".env"}


settings = Settings()
