# OTT Messaging System

Web application for the OTT Messaging System project.

The current repository contains the frontend UI built with:

* React
* TypeScript
* Vite
* Bootstrap
* Docker
* Node.js 22

The frontend is currently run in development mode using Docker Compose.

---

## 1. Project Structure

```text
OTT_messaging_system/
├── docker-compose.yml
│
├── docs/
│   ├── 00_overview/
│   ├── 01_architecture/
│   └── resources/
│
└── ui/
    ├── public/
    ├── src/
    │   ├── assets/
    │   ├── components/
    │   ├── pages/
    │   ├── types/
    │   ├── utils/
    │   ├── App.css
    │   ├── App.tsx
    │   ├── index.css
    │   └── main.tsx
    │
    ├── .gitignore
    ├── eslint.config.js
    ├── index.html
    ├── package.json
    ├── package-lock.json
    ├── README.md
    ├── tsconfig.app.json
    ├── tsconfig.json
    ├── tsconfig.node.json
    └── vite.config.ts
```

---

# 2. Prerequisites

Before running the project, make sure the following software is installed.

### Git

Check Git:

```bash
git --version
```

### Docker

Check Docker:

```bash
docker --version
```

### Docker Compose

Check Docker Compose:

```bash
docker compose version
```

Docker Desktop is recommended on Windows and macOS.

> You do not need to install Node.js locally to run the UI through Docker.

The project uses the following Docker image:

```text
node:22-alpine
```

---

# 3. Clone the Repository

Clone the repository from GitHub:

```bash
git clone <REPOSITORY_URL>
```

For example:

```bash
git clone https://github.com/<username>/OTT_messaging_system.git
```

Move into the project directory:

```bash
cd OTT_messaging_system
```

Check the project structure:

```bash
dir
```

On Linux/macOS:

```bash
ls
```

You should see:

```text
docker-compose.yml
docs/
ui/
```

---

# 4. Docker Compose Configuration

The root `docker-compose.yml` contains the UI service:

```yaml
services:

  ui:
    image: node:22-alpine
    working_dir: /app

    ports:
      - "5173:5173"

    volumes:
      - ./ui:/app
      - ui_node_modules:/app/node_modules

    command: npm run dev -- --host 0.0.0.0

    stdin_open: true
    tty: true

volumes:
  ui_node_modules:
```

## Configuration explanation

### `image: node:22-alpine`

```yaml
image: node:22-alpine
```

Uses Node.js 22 with Alpine Linux as the base image.

---

### `working_dir: /app`

```yaml
working_dir: /app
```

Sets `/app` as the working directory inside the container.

The local `ui/` directory is mounted to this location.

---

### `ports`

```yaml
ports:
  - "5173:5173"
```

Maps:

```text
Host:      5173
Container: 5173
```

Therefore, the Vite development server can be accessed at:

```text
http://localhost:5173
```

---

### `volumes`

```yaml
volumes:
  - ./ui:/app
  - ui_node_modules:/app/node_modules
```

The first volume:

```yaml
- ./ui:/app
```

mounts the local `ui` folder into `/app`.

This means changes made to the source code on the host machine are available inside the container.

The second volume:

```yaml
- ui_node_modules:/app/node_modules
```

stores `node_modules` in a Docker-managed volume.

This prevents the container's `node_modules` from being replaced by a potentially incompatible local `node_modules`.

---

### `command`

```yaml
command: npm run dev -- --host 0.0.0.0
```

Starts the Vite development server.

The important part is:

```text
--host 0.0.0.0
```

This allows Vite to accept connections from outside the container.

Without it, the application may only listen on the container's localhost and may not be accessible through:

```text
http://localhost:5173
```

---

# 5. Start the Project

From the root directory:

```bash
docker compose up
```

You should see output similar to:

```text
ui-1  | VITE v...
ui-1  | Local:   http://localhost:5173/
ui-1  | Network: http://172.x.x.x:5173/
```

Open your browser:

```text
http://localhost:5173
```

The React application should now be running.

---

# 6. Run in Background

If you do not want Docker Compose to occupy the terminal:

```bash
docker compose up -d
```

Check running containers:

```bash
docker compose ps
```

Example:

```text
NAME                       STATUS
OTT_messaging_system-ui-1  Up
```

Open:

```text
http://localhost:5173
```

---

# 7. Stop the Project

If the project is running in the foreground:

```text
Ctrl + C
```

Or stop the services explicitly:

```bash
docker compose down
```

This stops and removes the containers created by Docker Compose.

---

# 8. Restart the Project

After stopping the project:

```bash
docker compose up
```

Or run in background:

```bash
docker compose up -d
```

---

# 9. Rebuild / Recreate the Container

Because the current configuration uses:

```yaml
image: node:22-alpine
```

there is no custom Dockerfile to build.

Normally you can simply run:

```bash
docker compose up
```

If you want to recreate the container:

```bash
docker compose up --force-recreate
```

---

# 10. Installing Dependencies

After cloning the repository, the `ui` directory contains:

```text
package.json
package-lock.json
```

The project dependencies should be installed inside the Docker container.

Enter the UI container:

```bash
docker compose exec ui sh
```

Then run:

```bash
npm install
```

After installation:

```bash
exit
```

Then restart:

```bash
docker compose restart ui
```

Alternatively, if the container is not running, start it first:

```bash
docker compose up -d
```

Then:

```bash
docker compose exec ui sh
npm install
```

---

# 11. Check Installed Dependencies

To check the dependencies inside the container:

```bash
docker compose exec ui npm list
```

You can also check the Node.js version:

```bash
docker compose exec ui node --version
```

Expected:

```text
v22.x.x
```

Check npm:

```bash
docker compose exec ui npm --version
```

---

# 12. View Logs

If the application does not work, check the container logs.

```bash
docker compose logs ui
```

To follow the logs in real time:

```bash
docker compose logs -f ui
```

Press:

```text
Ctrl + C
```

to stop viewing the logs.

---

# 13. Check Container Status

Run:

```bash
docker compose ps
```

A healthy development environment should show the `ui` service as running.

You can also check all Docker containers:

```bash
docker ps
```

---

# 14. Troubleshooting

## 14.1 `docker` command not found

Example:

```text
'docker' is not recognized as an internal or external command
```

### Cause

Docker is not installed or Docker is not available in the system PATH.

### Solution

Install Docker Desktop and restart the terminal.

Then verify:

```bash
docker --version
```

And:

```bash
docker compose version
```

---

# 14.2 Docker Desktop is not running

You may see errors such as:

```text
Cannot connect to the Docker daemon
```

### Solution

Start Docker Desktop and wait until Docker is fully running.

Then:

```bash
docker info
```

If Docker is working, this command should return Docker information instead of a connection error.

---

# 14.3 `docker compose` is not recognized

Try:

```bash
docker compose version
```

If this does not work, make sure you are using a recent Docker Desktop installation.

Do not confuse:

```bash
docker-compose
```

with:

```bash
docker compose
```

This project uses the newer Docker Compose syntax:

```bash
docker compose ...
```

---

# 14.4 Port `5173` is already in use

You may see an error similar to:

```text
Bind for 0.0.0.0:5173 failed: port is already allocated
```

### Cause

Another application or container is already using port `5173`.

### Check Docker containers

```bash
docker ps
```

If another container is using the port, stop it:

```bash
docker stop <container_name>
```

You can also check whether another Docker Compose project is running:

```bash
docker compose ls
```

### Alternative

Change the host port in `docker-compose.yml`:

```yaml
ports:
  - "5174:5173"
```

The first number is the host port.

The second number is the container port.

Then access:

```text
http://localhost:5174
```

The Vite server inside the container is still using port `5173`.

---

# 14.5 Browser shows `ERR_CONNECTION_REFUSED`

If:

```text
http://localhost:5173
```

does not open, first check:

```bash
docker compose ps
```

Then check:

```bash
docker compose logs ui
```

Make sure the Vite server started successfully.

The command in `docker-compose.yml` should be:

```yaml
command: npm run dev -- --host 0.0.0.0
```

The `--host 0.0.0.0` option is important when running Vite inside Docker.

---

# 14.6 Container is not running

If you see:

```text
service "ui" is not running
```

for a command such as:

```bash
docker compose exec ui sh
```

start the service first:

```bash
docker compose up -d
```

Then check:

```bash
docker compose ps
```

After the container is running:

```bash
docker compose exec ui sh
```

---

# 14.7 `npm run dev` fails

Check the logs:

```bash
docker compose logs ui
```

Then check whether dependencies exist:

```bash
docker compose exec ui ls
```

You should see:

```text
node_modules
package.json
package-lock.json
src
...
```

If `node_modules` is missing or corrupted, reinstall dependencies:

```bash
docker compose exec ui npm install
```

Then restart:

```bash
docker compose restart ui
```

---

# 14.8 `Cannot find module` / dependency errors

Example:

```text
Cannot find module ...
```

or:

```text
Failed to resolve import ...
```

Try reinstalling dependencies.

First stop the project:

```bash
docker compose down
```

Remove the Docker `node_modules` volume:

```bash
docker compose down -v
```

Then start again:

```bash
docker compose up -d
```

Install dependencies:

```bash
docker compose exec ui npm install
```

Check the logs:

```bash
docker compose logs -f ui
```

> `docker compose down -v` removes Docker-managed volumes associated with this Compose project. Do not use it if the project has other important persistent Docker volumes you need to keep.

---

# 14.9 Changes in React code are not reflected

The project uses:

```yaml
- ./ui:/app
```

so the source code should be synchronized between the host and container.

If changes are not reflected, check that the container is running:

```bash
docker compose ps
```

Then check Vite logs:

```bash
docker compose logs -f ui
```

You can also restart the UI:

```bash
docker compose restart ui
```

Then refresh:

```text
http://localhost:5173
```

---

# 14.10 `npm install` works but the app still has errors

First check:

```bash
docker compose logs ui
```

Then verify:

```bash
docker compose exec ui node --version
```

and:

```bash
docker compose exec ui npm --version
```

Check the project dependencies:

```bash
docker compose exec ui npm list
```

If the dependency state appears corrupted, recreate the `node_modules` volume:

```bash
docker compose down -v
docker compose up -d
docker compose exec ui npm install
```

---

# 15. Common Docker Commands

### Start

```bash
docker compose up
```

### Start in background

```bash
docker compose up -d
```

### Stop

```bash
docker compose down
```

### Restart UI

```bash
docker compose restart ui
```

### Check status

```bash
docker compose ps
```

### View logs

```bash
docker compose logs ui
```

### Follow logs

```bash
docker compose logs -f ui
```

### Open a shell inside the container

```bash
docker compose exec ui sh
```

### Install dependencies

```bash
docker compose exec ui npm install
```

### Check Node.js version

```bash
docker compose exec ui node --version
```

### Check npm version

```bash
docker compose exec ui npm --version
```

---

# 16. Recommended First-Time Setup

After cloning the repository, the recommended workflow is:

```bash
git clone <REPOSITORY_URL>
```

```bash
cd OTT_messaging_system
```

```bash
docker compose up -d
```

Install dependencies:

```bash
docker compose exec ui npm install
```

Check the service:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs -f ui
```

Then open:

```text
http://localhost:5173
```

---

# 17. Development Workflow

During development, source code is located in:

```text
ui/src/
```

For example:

```text
ui/src/
├── assets/
├── components/
├── pages/
├── types/
├── utils/
├── App.css
├── App.tsx
├── index.css
└── main.tsx
```

Edit the files normally on the host machine.

Because:

```yaml
- ./ui:/app
```

is configured, the changes are mounted into the container.

Vite should automatically detect source changes and update the browser through Hot Module Replacement (HMR).

---

# 18. Important Notes

### Node.js does not need to be installed locally

The project uses:

```yaml
image: node:22-alpine
```

Therefore Node.js runs inside Docker.

You mainly need:

```text
Git
Docker Desktop
```

---

### Do not commit `node_modules`

The `node_modules` directory should not be committed to Git.

The project uses:

```text
package.json
package-lock.json
```

to define the dependencies.

Docker stores the installed dependencies in:

```text
ui_node_modules
```

---

### Do not manually create `node_modules` for Git

After cloning the repository, you do not need to copy `node_modules` from another computer.

Run:

```bash
docker compose exec ui npm install
```

Docker will create the dependency environment inside the container volume.

---

# 19. Quick Start

For users who only need the essential commands:

```bash
git clone <REPOSITORY_URL>
cd OTT_messaging_system
docker compose up -d
docker compose exec ui npm install
```

Then open:

```text
http://localhost:5173
```

To stop:

```bash
docker compose down
```

To start again:

```bash
docker compose up -d
```

To check problems:

```bash
docker compose ps
docker compose logs ui
```

---

# 20. Troubleshooting Quick Reference

| Problem                      | Command / Solution                                        |
| ---------------------------- | --------------------------------------------------------- |
| Docker not found             | Install/start Docker Desktop                              |
| Docker daemon unavailable    | Start Docker Desktop                                      |
| Port 5173 occupied           | Stop the process/container using 5173 or change host port |
| UI container not running     | `docker compose up -d`                                    |
| Dependency missing           | `docker compose exec ui npm install`                      |
| Dependency appears corrupted | `docker compose down -v` then `npm install`               |
| UI cannot connect            | Check `docker compose logs ui`                            |
| Code changes not reflected   | Check container + Vite logs, then restart UI              |
| Need container shell         | `docker compose exec ui sh`                               |
| Need Node version            | `docker compose exec ui node --version`                   |

---

# 21. Repository Information

**Project:** OTT Messaging System

**Frontend:** React + TypeScript + Vite

**Runtime:** Node.js 22

**Container:** Docker

**Development Server:** Vite

**Default Port:** `5173`

**Frontend Directory:** `ui/`

**Docker Compose File:** `docker-compose.yml`
