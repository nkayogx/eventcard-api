package com.kayogx.eventcard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves uploaded files into a folder on this server (setting "app.uploads-folder")
 * and makes them downloadable at  <public-api-url>/uploads/<folder>/<file name>.
 */
@Configuration
public class LocalFileStorage implements FileStorage, WebMvcConfigurer {

    private final Path uploadsFolder;
    private final String publicApiUrl;

    public LocalFileStorage(@Value("${app.uploads-folder}") String uploadsFolder,
                            @Value("${app.public-api-url}") String publicApiUrl) {
        this.uploadsFolder = Path.of(uploadsFolder).toAbsolutePath().normalize();
        this.publicApiUrl = publicApiUrl;
    }

    @Override
    public String save(String folder, String fileName, byte[] content) {
        try {
            Path targetFolder = uploadsFolder.resolve(folder);
            Files.createDirectories(targetFolder);
            Files.write(targetFolder.resolve(fileName), content);
        } catch (IOException problem) {
            throw new UncheckedIOException("Could not save the uploaded file", problem);
        }
        return publicUrl(folder, fileName);
    }

    @Override
    public byte[] read(String folder, String fileName) {
        try {
            return Files.readAllBytes(uploadsFolder.resolve(folder).resolve(fileName));
        } catch (IOException problem) {
            throw new UncheckedIOException("Could not read the saved file " + folder + "/" + fileName, problem);
        }
    }

    @Override
    public String publicUrl(String folder, String fileName) {
        return publicApiUrl + "/uploads/" + folder + "/" + fileName;
    }

    /** Lets browsers download the saved files from /uploads/... */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String folderAddress = uploadsFolder.toUri().toString();
        // Spring needs the folder address to end with "/"
        if (!folderAddress.endsWith("/")) {
            folderAddress = folderAddress + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(folderAddress);
    }
}
