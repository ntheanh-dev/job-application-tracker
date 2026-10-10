# Job Application Tracker

A lightweight, local-first web application for tracking job applications and keeping each application linked to the exact CV that was submitted.

The application runs entirely on your Mac. It does not require a database, an account, or an external service, and it only listens on `127.0.0.1` by default.

## Features

- Add a job application with the job URL, experience level, position, and submitted CV.
- View all applications in a simple table, ordered by newest first.
- Open the original job posting or uploaded CV.
- Reveal a CV in Finder.
- Delete an application and its associated CV.
- Choose the CV storage folder with the native macOS folder picker.
- Automatically migrate existing CVs and update their stored paths when the folder changes.
- Persist application data and settings between restarts.
- Validate URLs, position values, experience values, file types, and upload size.
- Use the responsive interface on desktop and smaller screens.

## Technology

- Java 21
- Spring Boot 3.5
- Spring MVC and Thymeleaf
- HTML and CSS
- Jackson JSON storage
- Maven

## Requirements

- macOS
- Java 21 or newer
- Maven 3.9 or newer

Verify the installed versions:

```bash
java -version
mvn -version
```

## Running the application

### Quick start on macOS

Double-click `start.command`. The script starts Spring Boot and opens:

```text
http://localhost:8080
```

To stop the application, return to the Terminal window and press `Control + C`.

### Run from Terminal

```bash
cd /Users/user/workspace/job-application-tracker
mvn spring-boot:run
```

### Run from IntelliJ IDEA

1. Open the project directory in IntelliJ IDEA.
2. Load the project as a Maven project.
3. Select Java 21 as the Project SDK.
4. Run `JobTrackerApplication`.

If IntelliJ shows unresolved Spring methods while Maven builds successfully, use **Reload All Maven Projects** from the Maven tool window.

## Using the application

1. Enter the job posting URL.
2. Select the required experience and position.
3. Select the CV that was submitted.
4. Click **Save application**.

The table provides links to the job posting and CV. Its action buttons reveal the CV in Finder or delete the application.

Supported CV formats are PDF, DOC, and DOCX. The maximum upload size is 15 MB.

## CV storage configuration

The current CV folder is displayed below the page title.

To change it:

1. Click **Configure** next to the displayed path.
2. Enter a path or click **Choose folder…** to open the native macOS folder picker.
3. Click **Save configuration**.

When the folder changes, the application creates the destination if necessary, moves every available managed CV, and updates the corresponding JSON records. Completed moves are rolled back if the migration fails partway through. An unrelated destination file is never overwritten.

## Local data

The default application data directory is:

```text
~/Documents/JobApplicationTracker
```

It contains:

```text
JobApplicationTracker/
├── applications.json   # Application records
├── settings.json       # Selected CV directory
└── cvs/                # Default CV directory
```

CV filenames on disk use generated UUIDs to prevent collisions. The original filename is retained in the application record and displayed in the interface.

To change the entire application data directory at startup:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--jobtracker.data-dir=/absolute/path/to/data"
```

This changes the JSON data location and default CV folder. The CV folder can also be changed later from the web interface.

## Configuration

Default settings are defined in `src/main/resources/application.properties`:

```properties
server.port=8080
server.address=127.0.0.1
spring.servlet.multipart.max-file-size=15MB
spring.servlet.multipart.max-request-size=16MB
```

Binding to `127.0.0.1` keeps the application accessible only from the local machine.

To run on another port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

## Testing and packaging

Run the test suite:

```bash
mvn test
```

Build and run an executable JAR:

```bash
mvn clean package
java -jar target/job-application-tracker-1.0.0.jar
```

## Project structure

```text
src/
├── main/
│   ├── java/com/local/jobtracker/
│   │   ├── controller/    # Web routes and macOS integration
│   │   ├── model/         # Application record
│   │   ├── service/       # JSON persistence and CV file management
│   │   └── JobTrackerApplication.java
│   └── resources/
│       ├── static/css/    # Interface styles
│       ├── templates/     # Thymeleaf views
│       └── application.properties
└── test/                  # Spring Boot tests
```

## Privacy and scope

- Application records and CV files remain on the local machine.
- No analytics or external data storage is used.
- Job posting links open only when selected by the user.
- The native folder picker and Finder integration are macOS-specific.
