package com.local.jobtracker.controller;

import com.local.jobtracker.model.JobApplication;
import com.local.jobtracker.service.ApplicationStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {
    private static final List<String> POSITIONS = List.of("Frontend", "Backend", "Fullstack", "Mobile", "DevOps", "QA/QC", "Data");
    private final ApplicationStore store;

    public HomeController(ApplicationStore store) {
        this.store = store;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<JobApplication> applications = store.findAll();
        model.addAttribute("applications", applications);
        model.addAttribute("applicationCount", applications.size());
        model.addAttribute("positions", POSITIONS);
        model.addAttribute("cvDirectory", store.cvDirectory().toString());
        return "index";
    }

    @PostMapping("/applications")
    public String add(@RequestParam String applyLink,
                      @RequestParam int yearsOfExperience,
                      @RequestParam String position,
                      @RequestParam MultipartFile cv,
                      RedirectAttributes redirect) {
        try {
            URI uri = URI.create(applyLink.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Link apply phải bắt đầu bằng http:// hoặc https://.");
            }
            if (yearsOfExperience < 0 || yearsOfExperience > 20) {
                throw new IllegalArgumentException("Số năm kinh nghiệm không hợp lệ.");
            }
            if (!POSITIONS.contains(position)) {
                throw new IllegalArgumentException("Vị trí không hợp lệ.");
            }
            store.add(applyLink, yearsOfExperience, position, cv);
            redirect.addFlashAttribute("success", "Đã lưu application và CV.");
        } catch (Exception error) {
            redirect.addFlashAttribute("error", error.getMessage() == null ? "Không thể lưu application." : error.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/settings/cv-directory")
    public String updateCvDirectory(@RequestParam String cvDirectory, RedirectAttributes redirect) {
        try {
            store.updateCvDirectory(cvDirectory);
            redirect.addFlashAttribute("success", "Đã cập nhật thư mục lưu CV.");
        } catch (Exception error) {
            redirect.addFlashAttribute("error",
                    error.getMessage() == null ? "Không thể cập nhật thư mục lưu CV." : error.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/settings/choose-cv-directory")
    @ResponseBody
    public ResponseEntity<Map<String, String>> chooseCvDirectory() {
        try {
            Process process = new ProcessBuilder(
                    "osascript",
                    "-e", "POSIX path of (choose folder with prompt \"Chọn thư mục lưu CV\")"
            ).start();
            String selectedPath = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (error.contains("User canceled")) {
                    return ResponseEntity.status(409).body(Map.of("error", "Đã huỷ chọn thư mục."));
                }
                throw new IOException(error.isBlank() ? "Không thể mở cửa sổ chọn thư mục." : error);
            }
            return ResponseEntity.ok(Map.of("path", Path.of(selectedPath).normalize().toString()));
        } catch (Exception error) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", error.getMessage() == null ? "Không thể chọn thư mục." : error.getMessage()));
        }
    }

    @GetMapping("/cv/{id}")
    public ResponseEntity<Resource> openCv(@PathVariable String id, HttpServletRequest request) throws IOException {
        JobApplication item = store.findById(id).orElseThrow(() -> new IOException("Không tìm thấy CV."));
        Path path = store.cvPath(item);
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = request.getServletContext().getMimeType(path.toString());
        return ResponseEntity.ok()
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(item.cvOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @PostMapping("/cv/{id}/reveal")
    public String revealCv(@PathVariable String id, RedirectAttributes redirect) {
        try {
            JobApplication item = store.findById(id).orElseThrow(() -> new IOException("Không tìm thấy CV."));
            Path cv = store.cvPath(item);
            if (!Files.exists(cv)) {
                throw new IOException("File CV không còn tồn tại.");
            }
            new ProcessBuilder("open", "-R", cv.toString()).start();
            redirect.addFlashAttribute("success", "Đã mở vị trí CV trong Finder.");
        } catch (Exception error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/applications/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes redirect) {
        try {
            store.delete(id);
            redirect.addFlashAttribute("success", "Đã xoá application và file CV.");
        } catch (Exception error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/";
    }
}
