package service;

import java.util.List;

public record AutoIndexResult(
        List<AutoIndexFileResult> files,
        AutoIndexSummary summary) {
}
