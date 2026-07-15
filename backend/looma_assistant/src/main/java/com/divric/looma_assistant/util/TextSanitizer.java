package com.divric.looma_assistant.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TextSanitizer — houses static utility methods that child actors can call
 * to clean up text before sending prompts out, or strip formatting characters
 * from LLM responses before writing them to the output DTO.
 * <p>
 * Centralizing this logic here prevents string trimming and regex duplication
 * across the specialized child actors, improving token efficiency.
 */
public final class TextSanitizer {

    private static final Logger log = LoggerFactory.getLogger(TextSanitizer.class);

    private TextSanitizer() {
        // Utility class — no instantiation
    }

    /**
     * Sanitizes a prompt before sending it to an LLM:
     * - Trims leading/trailing whitespace
     * - Collapses multiple consecutive spaces into one
     * - Removes null (NUL) characters
     * - Normalizes line endings to Unix-style (\n)
     *
     * @param raw the raw prompt text
     * @return cleaned prompt text
     */
    public static String sanitizePrompt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String cleaned = raw
                .replace('\r', '\n')          // standardize line endings
                .replaceAll("\\x00", "")      // strip null chars
                .replaceAll("[ \\t]+", " ")   // collapse horizontal whitespace
                .trim();

        log.debug("sanitizePrompt: reduced from {} to {} chars", raw.length(), cleaned.length());
        return cleaned;
    }

    /**
     * Cleans an LLM response before storing it in a TaskOutput:
     * - Trims leading/trailing whitespace
     * - Strips common markdown fences (```, ~~~) if they wrap the entire output
     * - Removes null characters
     * - Normalizes line endings
     *
     * @param raw the raw response from the LLM
     * @return cleaned response text
     */
    public static String sanitizeResponse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String cleaned = raw
                .replace('\r', '\n')
                .replaceAll("\\x00", "")
                .trim();

        // Strip outer markdown fences if the whole response is wrapped
        if (cleaned.startsWith("```") && cleaned.endsWith("```") && cleaned.length() > 6) {
            cleaned = cleaned.substring(3, cleaned.length() - 3).trim();
        } else if (cleaned.startsWith("~~~") && cleaned.endsWith("~~~") && cleaned.length() > 6) {
            cleaned = cleaned.substring(3, cleaned.length() - 3).trim();
        }

        log.debug("sanitizeResponse: reduced from {} to {} chars", raw.length(), cleaned.length());
        return cleaned;
    }

    /**
     * Strips all non-printable characters from the text.
     * Useful before serializing DTOs to JSON.
     *
     * @param raw the raw text
     * @return text containing only printable ASCII / Unicode
     */
    public static String stripNonPrintable(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // Keep printable ASCII + standard Unicode categories (letters, marks, numbers, punctuation, symbols, spaces)
        String cleaned = raw.replaceAll("[^\\p{Print}\\p{L}\\p{M}\\p{N}\\p{P}\\p{S}\\p{Z}]", "");
        log.debug("stripNonPrintable: reduced from {} to {} chars", raw.length(), cleaned.length());
        return cleaned;
    }

    /**
     * Truncates text to a maximum token count (approximated as 4 characters per token).
     * Leaves the beginning and end intact, removing the middle.
     *
     * @param text            the text to truncate
     * @param maxTokenCount   maximum number of tokens allowed
     * @return truncated text
     */
    public static String truncateToTokens(String text, int maxTokenCount) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int maxChars = maxTokenCount * 4;
        if (text.length() <= maxChars) {
            return text;
        }
        // Keep first 60% and last 40% of the limit, with an ellipsis
        int headLen = (int) (maxChars * 0.6);
        int tailLen = (int) (maxChars * 0.4) - 3;
        if (tailLen < 10) {
            tailLen = 10;
            headLen = maxChars - tailLen - 3;
        }
        String truncated = text.substring(0, headLen) + "..." + text.substring(text.length() - tailLen);
        log.debug("truncateToTokens: reduced from {} to {} chars ({} tokens approx)", text.length(), truncated.length(), maxTokenCount);
        return truncated;
    }
}