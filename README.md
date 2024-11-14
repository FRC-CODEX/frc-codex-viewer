# FRC CODEx

## Applications

### Server
The server hosts the filing index and admin area and runs the indexing service.

To build and test:
```bash
./gradlew build
```

### Processor
As standalone application: Receives jobs from an SQS queue, processes them, 
published the result assets to S3, and pushes notification to another SQS queue.

As AWS Lambda function: Can be manually invoked for individual generations. Publishes
result assets to S3 and includes result details in invocation response.

To install dependencies:
```bash
pip install -r requirements-dev.txt
```

To run unit tests:
```bash
pytest processor_tests
```

To run code style tests:
```bash
flake8 .
```

To run typing tests:
```bash
mypy .
```

To prepare taxonomy packages:
1. Create `./dev/taxonomy_packages` directory.
2. Place taxonomy packages in the directory.


### Support
To avoid the need to maintain an authenticated application or area of the filing index,
support operations can be performed within the AWS console by invoking the `frc-codex-support`
Lambda function.


## Docker
The preferred method to run any part of the FRC CODEx is using Docker Compose.

### Setup

#### Secrets
To configure secrets for the server and processor, add them in a `frc-codex-server.secrets` file in the root directory. Example:
```properties
COMPANIES_HOUSE_REST_API_KEY=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
COMPANIES_HOUSE_STREAM_API_KEY=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
```

#### Local Configuration
Add `./dev/compose-local.yml` (untracked) to override Docker compose configuration.
This is required to add a volume for the processor to access your local installation of `frc-ixbrl-viewer`.
The processor expects an `iXBRLViewerPlugin` directory in the plugins directory: `/processor/plugins/iXBRLViewerPlugin`.

Example `./dev/compose-local.yml`:
```yaml
services:
  frc-codex-lambda:
    volumes:
      - ../../frc-ixbrl-viewer/iXBRLViewerPlugin:/var/task/processor/plugins/frc-ixbrl-viewer/iXBRLViewerPlugin
  frc-codex-processor:
    volumes:
      - ../../frc-ixbrl-viewer/iXBRLViewerPlugin:/processor/plugins/iXBRLViewerPlugin
  postgres:
    volumes:
      # Persists the database data in the tmp directory between startups
      - ./tmp/postgres:/var/lib/postgresql/data
```

### Compose
To build images and compose services:
```bash
    ./dev/env-setup.sh
```
Or, to build images and compose services separately, use `./dev/env-build.sh` and `./dev/env-compose.sh` respectively.

## Remote Debugging
You can attach to the composed Docker container within IntelliJ for debugging:
1. Create a "Remote JVM Debug" run configuration in IntelliJ.
2. Select "Attach to remote JVM" for debugger mode.
3. Enter "localhost" for host.
4. Enter "8180" for port (should match the port configured in JAVA_TOOL_OPTIONS).
5. Compose your docker environment
6. Run the "Remote JVM Debug" configuration in IntelliJ.
7. A successful attachment will show "Connected to the target VM (...)" or similar.

## Running Puppeteer Tests
All commands should be run from repository root.
1. Install npm. Instructions can be found here: <https://www.npmjs.com/get-npm>
2. Install the dependencies for puppeteer by running: `npm install`.
3. Start the docker containers by running: `SEEDED=true ./dev/env-setup.sh`
4. Run the tests by running: `npm run test`.

## Features

### Admin
An admin area exists at `/admin` for managing the filing index.
This area is not intended to be used in a production environment.
Instead, it is meant to be enabled in development and staging environments for testing and debugging purposes.

This feature is disabled by default and can be enabled with `ADMIN_ENABLED`.
Rudimentary "authentication" for the admin area can be enabled by setting `ADMIN_KEY`.
This will require a user to first navigate to `/admin/login/{ADMIN_KEY}` to gain access to the admin features.

> [!WARNING]
> This authentication is not secure and should not be used in a production environment.
