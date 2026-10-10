package com.local.jobtracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.local.jobtracker.model.JobApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApplicationStore {
    private static final long MAX_CV_BYTES = 15L * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".doc", ".docx");

    private final ObjectMapper objectMapper;
    private final Path dataDirectory;
    private final Path defaultCvDirectory;
    private final Path dataFile;
    private final Path settingsFile;
    private Path currentCvDirectory;
    private final List<JobApplication> applications = new ArrayList<>();

    public ApplicationStore(ObjectMapper objectMapper,
                            @Value("${jobtracker.data-dir:${user.home}/Documents/JobApplicationTracker}") String dataDir) {
        this.objectMapper = objectMapper;
        this.dataDirectory = Path.of(dataDir).toAbsolutePath().normalize();
        this.defaultCvDirectory = this.dataDirectory.resolve("cvs");
        this.currentCvDirectory = this.defaultCvDirectory;
        this.dataFile = this.dataDirectory.resolve("applications.json");
        this.settingsFile = this.dataDirectory.resolve("settings.json");
    }

    @PostConstruct
    public synchronized void initialize() throws IOException {
        Files.createDirectories(dataDirectory);
        if (Files.exists(settingsFile)) {
            Settings settings = objectMapper.readValue(settingsFile.toFile(), Settings.class);
            if (settings.cvDirectory() != null && !settings.cvDirectory().isBlank()) {
                currentCvDirectory = resolveUserPath(settings.cvDirectory());
            }
        }
        Files.createDirectories(currentCvDirectory);
        if (Files.exists(dataFile)) {
            applications.addAll(objectMapper.readValue(dataFile.toFile(), new TypeReference<>() {}));
        }
    }

    public synchronized List<JobApplication> findAll() {
        return applications.stream()
                .sorted(Comparator.comparing(JobApplication::createdAt).reversed())
                .toList();
    }

    public synchronized Optional<JobApplication> findById(String id) {
        return applications.stream().filter(item -> item.id().equals(id)).findFirst();
    }

    public synchronized JobApplication add(String applyLink, int yearsOfExperience, String position,
                                           MultipartFile cv) throws IOException {
        validateCv(cv);
        String id = UUID.randomUUID().toString();
        String originalName = safeOriginalName(cv.getOriginalFilename());
        String extension = extensionOf(originalName);
        String storedName = id + extension;
        Path destination = currentCvDirectory.resolve(storedName).normalize();
        if (!destination.startsWith(currentCvDirectory)) {
            throw new IOException("Đường dẫn CV không hợp lệ.");
        }

        try (InputStream input = cv.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        JobApplication application = new JobApplication(
                id, applyLink.trim(), yearsOfExperience, position, originalName, storedName,
                currentCvDirectory.toString(), LocalDateTime.now());
        applications.add(application);
        try {
            persist();
        } catch (IOException error) {
            applications.remove(application);
            Files.deleteIfExists(destination);
            throw error;
        }
        return application;
    }

    public synchronized void delete(String id) throws IOException {
        JobApplication item = findById(id).orElseThrow(() -> new IOException("Không tìm thấy application."));
        applications.remove(item);
        persist();
        Files.deleteIfExists(cvPath(item));
    }

    public Path cvPath(JobApplication item) {
        Path itemDirectory = item.cvDirectory() == null || item.cvDirectory().isBlank()
                ? defaultCvDirectory
                : resolveUserPath(item.cvDirectory());
        Path path = itemDirectory.resolve(item.cvStoredName()).normalize();
        if (!path.startsWith(itemDirectory)) {
            throw new IllegalArgumentException("Đường dẫn CV không hợp lệ.");
        }
        return path;
    }

    public synchronized Path cvDirectory() {
        return currentCvDirectory;
    }

    public synchronized void updateCvDirectory(String directory) throws IOException {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập đường dẫn thư mục lưu CV.");
        }
        Path newDirectory = resolveUserPath(directory);
        Files.createDirectories(newDirectory);
        if (!Files.isDirectory(newDirectory) || !Files.isWritable(newDirectory)) {
            throw new IOException("Thư mục không tồn tại hoặc không có quyền ghi.");
        }
        if (newDirectory.equals(currentCvDirectory)) {
            return;
        }

        List<JobApplication> originalApplications = new ArrayList<>(applications);
        Map<Path, Path> movedFiles = new HashMap<>();
        List<JobApplication> updatedApplications = new ArrayList<>();
        try {
            for (JobApplication item : originalApplications) {
                Path source = cvPath(item);
                Path target = newDirectory.resolve(item.cvStoredName()).normalize();
                if (!target.startsWith(newDirectory)) {
                    throw new IOException("Đường dẫn CV đích không hợp lệ.");
                }

                if (Files.exists(source) && !source.equals(target)) {
                    if (Files.exists(target)) {
                        throw new IOException("File đã tồn tại trong thư mục mới: " + target.getFileName());
                    }
                    moveFile(source, target);
                    movedFiles.put(target, source);
                    updatedApplications.add(withCvDirectory(item, newDirectory));
                } else if (source.equals(target)) {
                    updatedApplications.add(withCvDirectory(item, newDirectory));
                } else {
                    // Giữ nguyên đường dẫn record nếu file nguồn đã bị xoá bên ngoài ứng dụng.
                    updatedApplications.add(item);
                }
            }

            applications.clear();
            applications.addAll(updatedApplications);
            persist();
            persistSettings(newDirectory);
            currentCvDirectory = newDirectory;
        } catch (Exception error) {
            applications.clear();
            applications.addAll(originalApplications);
            for (Map.Entry<Path, Path> move : movedFiles.entrySet()) {
                if (Files.exists(move.getKey())) {
                    try {
                        moveFile(move.getKey(), move.getValue());
                    } catch (IOException ignored) {
                        // Giữ lỗi gốc; file vẫn còn ở thư mục mới nếu rollback thất bại.
                    }
                }
            }
            try {
                persist();
            } catch (IOException ignored) {
                // Giữ lỗi gốc.
            }
            if (error instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Không thể chuyển CV sang thư mục mới.", error);
        }
    }

    private void validateCv(MultipartFile cv) {
        if (cv == null || cv.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file CV.");
        }
        if (cv.getSize() > MAX_CV_BYTES) {
            throw new IllegalArgumentException("CV không được lớn hơn 15 MB.");
        }
        String extension = extensionOf(safeOriginalName(cv.getOriginalFilename()));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("CV phải là file PDF, DOC hoặc DOCX.");
        }
    }

    private String safeOriginalName(String name) {
        String fallback = (name == null || name.isBlank()) ? "cv.pdf" : name;
        return Path.of(fallback).getFileName().toString();
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : "";
    }

    private Path resolveUserPath(String value) {
        String path = value.trim();
        if (path.equals("~")) {
            path = System.getProperty("user.home");
        } else if (path.startsWith("~/")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    private JobApplication withCvDirectory(JobApplication item, Path directory) {
        return new JobApplication(item.id(), item.applyLink(), item.yearsOfExperience(), item.position(),
                item.cvOriginalName(), item.cvStoredName(), directory.toString(), item.createdAt());
    }

    private void moveFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private void persistSettings(Path directory) throws IOException {
        Path temporary = dataDirectory.resolve("settings.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(temporary.toFile(), new Settings(directory.toString()));
        try {
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void persist() throws IOException {
        Files.createDirectories(dataDirectory);
        Path temporary = dataDirectory.resolve("applications.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), applications);
        try {
            Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Settings(String cvDirectory) {}
}
