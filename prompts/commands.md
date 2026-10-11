



### Specify Init if specify CLI is already globally installed
~~~
specify init --here --force --integration codex
~~~

### One Shot Init, if you haven't installed the specify-cli globally and want to run it directly using uv
~~~
uvx --from git+https://github.com/github/spec-kit.git specify init --here --integration codex
~~~

### Clone open source petclininc repo at a specific commit ina. new working branch locally
~~~
./setup-rehearsal.sh spring-petclinic-rest-speckit speckit-visit-idempotency
~~~




