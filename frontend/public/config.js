// Runtime configuration seam — overwritten by the container entrypoint
// (docker/40-codepill-runtime-env.sh) so one immutable image serves any
// environment. Empty in dev: import.meta.env supplies local defaults.
window.__CODEPILL_ENV__ = {}
