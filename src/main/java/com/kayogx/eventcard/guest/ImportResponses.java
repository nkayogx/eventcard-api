package com.kayogx.eventcard.guest;

import java.util.List;

/** What the guest list upload answers with. */
public final class ImportResponses {

    private ImportResponses() {
    }

    /** A row that cannot be imported, e.g. { row: 14, message: "Phone number is not valid" }. */
    public record ImportProblem(int row, String message) {
    }

    /** A row skipped because its phone number is already on the guest list (or earlier in the file). */
    public record ImportDuplicate(int row, String nameOnCard, String phone) {
    }

    /** A row that is fine and will be (or was) imported. */
    public record ReadyRow(int row, String nameOnCard, String phone, String cardType, String groupName) {
    }

    /** Answer to "check file": nothing has been saved yet. {@code readyExamples} shows the first 10 good rows. */
    public record ImportPreview(int readyCount, List<ReadyRow> readyExamples,
                                List<ImportProblem> problems, List<ImportDuplicate> duplicates) {
    }

    /** Answer to "import": the good rows have been saved. */
    public record ImportResult(int importedCount, List<ImportProblem> problems, List<ImportDuplicate> duplicates) {
    }
}
