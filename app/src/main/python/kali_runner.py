"""Small execution bridge used by KaliCode Pocket's embedded Python runtime."""

import io
import os
import sys
import traceback
from contextlib import redirect_stderr, redirect_stdout


class _ConsoleInput(io.TextIOBase):
    """Bridge input() to a native dialog on the UI thread."""

    def readable(self):
        return True

    def readline(self, size=-1):
        try:
            from java import jclass
            value = jclass("com.saikishore.kalicodepocket.PythonRunActivity").requestInput()
        except Exception:
            return ""
        if value is None:
            return ""
        return str(value) + "\n"


class _LimitedOutput(io.StringIO):
    """Keep runaway print loops from exhausting the phone's memory."""

    LIMIT = 500_000

    def __init__(self):
        super().__init__()
        self.truncated = False

    def write(self, text):
        if self.tell() >= self.LIMIT:
            self.truncated = True
            return len(text)
        remaining = self.LIMIT - self.tell()
        if len(text) > remaining:
            self.truncated = True
            super().write(text[:remaining])
            return len(text)
        return super().write(text)


def run_file(path):
    """Execute a Python file as __main__ and return captured stdout/stderr."""
    script = os.path.abspath(path)
    output = _LimitedOutput()
    old_argv = list(sys.argv)
    old_path = list(sys.path)
    old_stdin = sys.stdin
    old_cwd = os.getcwd()

    try:
        sys.argv = [script]
        sys.path.insert(0, os.path.dirname(script))
        sys.stdin = _ConsoleInput()
        os.chdir(os.path.dirname(script))
        with open(script, "r", encoding="utf-8") as handle:
            source = handle.read()
        globals_dict = {
            "__name__": "__main__",
            "__file__": script,
            "__package__": None,
            "__spec__": None,
            "__builtins__": __builtins__,
        }
        with redirect_stdout(output), redirect_stderr(output):
            exec(compile(source, script, "exec"), globals_dict, globals_dict)
    except SystemExit as exc:
        if exc.code not in (None, 0):
            print(f"Process exited with code {exc.code}", file=output)
    except BaseException as exc:
        tb = exc.__traceback__
        if tb is not None:
            tb = tb.tb_next  # hide the runner's own exec frame
        traceback.print_exception(type(exc), exc, tb, file=output)
    finally:
        sys.argv = old_argv
        sys.path[:] = old_path
        sys.stdin = old_stdin
        os.chdir(old_cwd)

    text = output.getvalue()
    if output.truncated:
        text += "\n\n[Output truncated at 500,000 characters.]"
    return text if text.strip() else "(The program finished without printing anything.)"
