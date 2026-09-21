package com.kayogx.eventcard.storage;

/**
 * Saves uploaded files (such as company logos and card artwork) and gives back a public link.
 *
 * Today files go to a folder on this server ({@link LocalFileStorage}).
 * Later we can add a cloud version (e.g. Amazon S3) without changing any other code.
 */
public interface FileStorage {

    /**
     * @param folder   a sub-folder to keep things tidy, e.g. "logos"
     * @param fileName the name to save the file under, e.g. "company-123.png"
     * @param content  the file's bytes
     * @return the public web address of the saved file
     */
    String save(String folder, String fileName, byte[] content);

    /** Reads back a file saved earlier with {@link #save}. */
    byte[] read(String folder, String fileName);

    /** The public web address of a saved file. */
    String publicUrl(String folder, String fileName);
}
