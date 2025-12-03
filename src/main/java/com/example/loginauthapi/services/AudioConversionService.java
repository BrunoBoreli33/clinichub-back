package com.example.loginauthapi.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class AudioConversionService {

    /**
     * Verifica se o FFmpeg está disponível ao iniciar a aplicação
     */
    @PostConstruct
    public void checkFFmpegAvailability() {
        try {
            log.info("🔍 Verificando disponibilidade do FFmpeg...");

            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Ler output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains("ffmpeg version")) {
                    log.info("✅ FFmpeg está disponível: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("✅ FFmpeg está funcionando corretamente!");
            } else {
                log.error("❌ FFmpeg retornou código de erro: {}", exitCode);
            }
        } catch (IOException e) {
            log.error("❌ FFmpeg NÃO está instalado ou não está no PATH!");
            log.error("⚠️ A conversão de áudio para iOS NÃO funcionará!");
            log.error("📖 Consulte o arquivo INSTALACAO_FFMPEG_COMPLETO.md para instruções");
        } catch (Exception e) {
            log.error("❌ Erro ao verificar FFmpeg: {}", e.getMessage());
        }
    }

    /**
     * Converte áudio Base64 para formato OGG com codec Opus
     * Compatível com WhatsApp iOS
     */
    public String convertToOggOpus(String audioBase64) throws IOException, InterruptedException {

        long startTime = System.currentTimeMillis();

        // Remover o prefixo data:audio/...;base64, se existir
        String base64Data = audioBase64;
        if (audioBase64.contains(",")) {
            base64Data = audioBase64.split(",")[1];
        }

        // Decodificar Base64
        byte[] audioBytes = Base64.getDecoder().decode(base64Data);

        // Criar arquivo temporário para o áudio de entrada
        Path inputPath = Files.createTempFile("audio_input_" + UUID.randomUUID(), ".webm");
        Files.write(inputPath, audioBytes);

        // Criar arquivo temporário para o áudio de saída
        Path outputPath = Files.createTempFile("audio_output_" + UUID.randomUUID(), ".ogg");

        try {
            log.info("🔄 Iniciando conversão de áudio para OGG/Opus");
            log.info("   Input: {} ({} bytes)", inputPath.getFileName(), audioBytes.length);

            // Comando FFmpeg para MÁXIMA QUALIDADE de áudio
            // Configurações premium para melhor experiência no WhatsApp
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-i", inputPath.toString(),           // Arquivo de entrada
                    "-c:a", "libopus",                    // Codec Opus
                    "-b:a", "128k",                       // ✅ Bitrate 128kbps (MÁXIMA QUALIDADE)
                    "-ar", "48000",                       // Sample rate 48kHz
                    "-ac", "1",                           // Mono (1 canal)
                    "-threads", "0",                      // Usar todos os cores disponíveis
                    "-compression_level", "0",            // ✅ Sem compressão extra (mais rápido e melhor qualidade)
                    "-frame_duration", "20",              // ✅ Frame 20ms (melhor qualidade)
                    "-application", "voip",               // Otimizado para voz
                    "-packet_loss", "0",                  // ✅ Sem perda de pacotes
                    "-vbr", "on",                         // ✅ Variable bitrate (adaptativo)
                    "-y",                                 // Sobrescrever arquivo de saída
                    outputPath.toString()                 // Arquivo de saída
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capturar output do FFmpeg para debug (apenas se houver erro)
            StringBuilder ffmpegOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ffmpegOutput.append(line).append("\n");
                    log.debug("FFmpeg: {}", line);
                }
            }

            // Timeout de 30 segundos para conversão
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("❌ FFmpeg timeout após 30 segundos");
                throw new RuntimeException("Timeout na conversão de áudio");
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                log.error("❌ FFmpeg falhou com código de saída: {}", exitCode);
                log.error("Output do FFmpeg:\n{}", ffmpegOutput.toString());
                throw new RuntimeException("Falha na conversão de áudio. Código: " + exitCode);
            }

            // Ler arquivo convertido
            byte[] convertedBytes = Files.readAllBytes(outputPath);
            String convertedBase64 = Base64.getEncoder().encodeToString(convertedBytes);

            long duration = System.currentTimeMillis() - startTime;

            log.info("✅ Áudio convertido com sucesso em {}ms!", duration);
            log.info("   Output: {} ({} bytes)", outputPath.getFileName(), convertedBytes.length);
            log.info("   Formato: OGG/Opus 48kHz Mono 128kbps (MÁXIMA QUALIDADE)");
            log.info("   Redução: {:.1f}%", (1 - (double)convertedBytes.length / audioBytes.length) * 100);

            // Adicionar prefixo data URL para compatibilidade
            return "data:audio/ogg;codecs=opus;base64," + convertedBase64;

        } finally {
            // Limpar arquivos temporários
            try {
                Files.deleteIfExists(inputPath);
                Files.deleteIfExists(outputPath);
                log.debug("🧹 Arquivos temporários removidos");
            } catch (IOException e) {
                log.warn("⚠️ Erro ao remover arquivos temporários: {}", e.getMessage());
            }
        }
    }

    /**
     * Verifica se o FFmpeg está disponível no sistema
     */
    public boolean isFFmpegAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ffmpeg", "-version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("❌ FFmpeg não está disponível: {}", e.getMessage());
            return false;
        }
    }
}