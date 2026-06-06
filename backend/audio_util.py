"""audioop compatibility — stdlib removed in Python 3.13+."""

try:
    import audioop  # noqa: F401
except ModuleNotFoundError:  # pragma: no cover
    import audioop_lts as audioop  # type: ignore[no-redef]

__all__ = ["audioop"]
