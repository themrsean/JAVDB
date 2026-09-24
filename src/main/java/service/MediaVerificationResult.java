package service;

import java.util.List;

public record MediaVerificationResult(
        List<MediaVerificationFileResult> files) {
}
