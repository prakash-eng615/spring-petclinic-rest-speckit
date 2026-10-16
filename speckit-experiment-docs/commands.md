### Specify Init if specify CLI is already globally installed
~~~
specify init --here --force --integration codex
~~~

### One Shot Init, if you haven't installed the specify-cli globally and want to run it directly using uv
~~~
uvx --from git+https://github.com/github/spec-kit.git specify init --here --integration codex
~~~
