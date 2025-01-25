package com.example.client_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud_name}") String cloudName,
            @Value("${cloudinary.api_key}") String apiKey,
            @Value("${cloudinary.api_secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    /**
     * Sube una imagen a Cloudinary dentro de la carpeta específica "sortec_vouchers" y devuelve la URL de la imagen subida.
     */
    public String uploadImage(MultipartFile file) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "sortec_vouchers",  // Subir a la carpeta especificada
                "use_filename", true,         // Mantiene el nombre original del archivo
                "unique_filename", false,     // No agrega ID aleatorio al nombre
                "resource_type", "image"      // Asegura que se trata como imagen
        ));

        // Obtener la URL segura de Cloudinary
        return uploadResult.get("secure_url").toString();
    }
}
