"""MCP Server for Code Q&A Agent.

Exposes a high-level `ask_repo_question` tool that runs the full
ReAct agent loop internally and returns the final answer.

Usage:
    python mcp_server.py                          # stdio (default)
    python mcp_server.py --transport sse          # SSE on port 3001
    python mcp_server.py --transport streamable-http  # Streamable HTTP on port 3001
"""

import argparse
import logging
import os

from mcp.server.fastmcp import FastMCP, Context

from agent import CodeQAAgent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(name)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

def _get_repo_name() -> str:
    """Derive repo name from workspace path (prefer host path in Docker)."""
    for var in ["CQA_HOST_WORKSPACE_PATH", "CQA_WORKSPACE_PATH"]:
        path = os.environ.get(var, "").rstrip("/")
        name = os.path.basename(path) if path else ""
        if name and name not in (".", "workspace", ""):
            return name
    return "codebase"


REPO_NAME = _get_repo_name()


def create_mcp_server(host: str = "0.0.0.0", port: int = 3001) -> FastMCP:
    mcp = FastMCP(
        "code-qa-agent",
        host=host,
        port=port,
        stateless_http=True,
        json_response=True,
    )

    @mcp.tool(
        name="ask_repo_question",
        description=f"Ask a question about the {REPO_NAME} codebase. "
        f"The agent explores the {REPO_NAME} repository using grep, file reading, "
        "AST symbol extraction and other code navigation tools, then returns a "
        "concise answer with file paths and line number references.",
    )
    async def ask_repo_question(question: str, ctx: Context) -> str:
        """Run the agent and return the answer."""
        logger.info(f"MCP tool call: ask_repo_question({question!r})")
        agent = CodeQAAgent()

        async def on_progress(iteration: int, max_iter: int, tool_name: str | None = None):
            msg = f"Iteration {iteration}/{max_iter}"
            if tool_name:
                msg += f" — calling {tool_name}"
            await ctx.report_progress(progress=iteration, total=max_iter, message=msg)
            await ctx.info(msg)

        answer = await agent.ask(question, progress_callback=on_progress)
        logger.info(f"MCP tool answer ({len(answer)} chars)")
        return answer

    return mcp


def main():
    parser = argparse.ArgumentParser(description="Code Q&A MCP Server")
    parser.add_argument(
        "--transport",
        choices=["stdio", "sse", "streamable-http"],
        default="stdio",
        help="MCP transport (default: stdio)",
    )
    parser.add_argument("--host", default="0.0.0.0", help="Bind host (SSE/HTTP)")
    parser.add_argument("--port", type=int, default=3001, help="Bind port (SSE/HTTP)")
    args = parser.parse_args()

    mcp = create_mcp_server(host=args.host, port=args.port)
    logger.info(f"Starting MCP server with transport={args.transport}")
    mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
