# Job Application Tracker

A lightweight, local-first web application for tracking job applications and keeping each application linked to the exact CV submitted.

The application runs on macOS using Spring Boot and stores data in local JSON files. No database, user account, or external service is required. By default, the server listens only on `127.0.0.1`.

## Features

- Add applications with a job posting URL, years of experience, position, and CV.
- View applications in a table, newest first.
- Open job posting links and uploaded CVs.
- Reveal CV files in macOS Finder.
- Delete applications and their associated CV files.
- Choose a custom CV directory using a path or the native macOS folder picker.
- Move managed CVs and update their saved paths when the CV directory changes.
- Persist applications and settings in JSON files between restarts.
- Validate job URLs, experience, positions, CV extensions, and upload size.
- Use a responsive interface on desktop and smaller screens.

## Technology

- Java 21
- Spring Boot 3.5
- Spring MVC and Thymeleaf
- HTML and CSS
- Jackson for JSON persistence
- Maven

## Requirements

- macOS (required for the folder picker and Finder integration)
- Java 21 or newer
- Maven 3.9 or newer

Verify your installation:

```bash
java -version
mvn -version
```

## Quick start

### Run from Terminal

From the project root:

```bash
mvn spring-boot:run
```

Open **http://localhost:1234** in your browser.

Press `Control + C` in the Terminal to stop the application.

### Run from IntelliJ IDEA

1. Open the project as a Maven project.
2. Set the Project SDK to Java 21.
3. Run `JobTrackerApplication`.
4. Open **http://localhost:1234**.

If IntelliJ displays unresolved Maven dependencies, use **Reload All Maven Projects** in the Maven tool window.

### Run with `start.command` (if present)

You can double-click `start.command` to start the app if the script is included in your project. Ensure the script opens **http://localhost:1234** and uses the project root as its working directory.

## Using the application

1. Enter the job posting URL.
2. Select the required years of experience and position.
3. Upload the CV submitted for that job.
4. Click **Save application**.

The application list includes links to the original posting and CV, as well as actions to reveal the CV in Finder or delete the application.

Supported CV formats: **PDF, DOC, DOCX**. Maximum file size: **15 MB**.

## Local data storage

The recommended data directory is **`./data`**, relative to the application's working directory. When running Maven from the project root, this is the project's `data/` folder.

To enable it, add the following to `src/main/resources/application.properties`:

```properties
jobtracker.data-dir=./data
```

The resulting layout is:

```text
job-application-tracker/
├── data/
│   ├── applications.json   # Saved application records (created when data is saved)
│   ├── settings.json       # Custom CV directory setting (created when changed)
│   └── cvs/                # Default uploaded CV directory
├── src/
│   └── main/
│       ├── java/
│       └── resources/
│           └── application.properties
├── pom.xml
└── .gitignore
```

The application creates the data and CV directories automatically at startup. JSON files are written when the relevant data or setting is saved.

CV files are stored with generated UUID-based filenames to avoid collisions. The original filename is retained in the application record and displayed in the interface.

**Why not `src/main/resources/data`?** Spring Boot packages resources inside the executable JAR. The application needs a writable directory for JSON changes and CV uploads, so runtime data belongs outside `src/main/resources`.

### Change the data directory

Override the directory at startup:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--jobtracker.data-dir=/absolute/path/to/data"
```

This changes the location of `applications.json`, `settings.json`, and the *default* `cvs/` directory. If `settings.json` already specifies a custom CV directory, the application uses that path instead.

**Important:** Changing the data directory does **not** automatically migrate existing JSON records or CVs. Also, application records retain their CV directory paths. When moving existing data, update stored CV paths as needed or use the CV directory configuration in the interface to move managed CVs before relocating the data directory.

### Keep personal data out of Git

Add the following to `.gitignore`:

```gitignore
/data/
```

## CV storage configuration

The current CV directory appears on the home page. To change it:

1. Click **Configure**.
2. Enter a folder path or click **Choose folder…** to use the macOS folder picker.
3. Click **Save configuration**.

The application creates the destination directory if needed, moves available managed CV files, and updates the corresponding JSON records. It does not overwrite an unrelated file in the destination. If migration fails partway through, it attempts to move already-moved files back.

This custom setting is persisted in `data/settings.json` and takes precedence over the default `data/cvs/` directory. CV paths in individual application records are also stored separately.

The native folder picker and **Reveal in Finder** actions require the application to run locally on macOS with access to the desktop session.

## Configuration

The relevant settings in `src/main/resources/application.properties` are:

```properties
spring.application.name=job-application-tracker
server.port=1234
server.address=127.0.0.1
jobtracker.data-dir=./data
server.servlet.session.tracking-modes=cookie
spring.servlet.multipart.max-file-size=15MB
spring.servlet.multipart.max-request-size=16MB
spring.jackson.serialization.write-dates-as-timestamps=false
spring.thymeleaf.cache=false
server.error.include-message=always
```

Binding to `127.0.0.1` limits access to the same computer.

To use a different port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

Open **http://localhost:9090** when using this override.

## Testing and packaging

Run tests:

```bash
mvn test
```

Build an executable JAR:

```bash
mvn clean package
```

Run the generated JAR (replace the filename with the actual artifact in `target/`):

```bash
java -jar target/job-application-tracker-1.0.0.jar
```

When using `jobtracker.data-dir=./data`, the directory is resolved relative to the **current working directory** of the Java process, not automatically relative to the JAR file. Run the JAR from the intended directory or specify an absolute data path:

```bash
java -jar target/job-application-tracker-1.0.0.jar \
  --jobtracker.data-dir=/absolute/path/to/data
```

## Project structure

```text
job-application-tracker/
├── data/                         # Writable local data (Git-ignored)
├── src/
│   ├── main/
│   │   ├── java/com/local/jobtracker/
│   │   │   ├── controller/       # HTTP routes and macOS integration
│   │   │   ├── model/            # Job application records
│   │   │   ├── service/          # JSON persistence and CV management
│   │   │   └── JobTrackerApplication.java
│   │   └── resources/
│   │       ├── static/css/       # Stylesheets
│   │       ├── templates/        # Thymeleaf views
│   │       └── application.properties
│   └── test/                     # Tests
├── pom.xml
└── .gitignore
```

## Privacy and scope

- Application records and uploaded CVs are stored locally.
- No analytics or external storage service is required.
- Job posting URLs are opened only when selected by the user.
- The folder picker and Finder integration are specific to macOS.
- This is a local, single-user utility, not a hosted multi-user application.
