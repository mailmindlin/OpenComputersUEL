package li.cil.oc.server.fs

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class FileSystemUtilsTest {

    @Test
    fun `test valid filename with basic characters`() {
        assertTrue(FileSystem.isValidFilename("file.txt"))
        assertTrue(FileSystem.isValidFilename("document.pdf"))
        assertTrue(FileSystem.isValidFilename("README.md"))
    }

    @Test
    fun `test valid filename with underscores and dashes`() {
        assertTrue(FileSystem.isValidFilename("my_file.txt"))
        assertTrue(FileSystem.isValidFilename("my-file.txt"))
        assertTrue(FileSystem.isValidFilename("file_name-123.txt"))
    }

    @Test
    fun `test valid filename with spaces`() {
        assertTrue(FileSystem.isValidFilename("my file.txt"))
        assertTrue(FileSystem.isValidFilename("file with spaces.txt"))
    }

    @Test
    fun `test valid filename with numbers`() {
        assertTrue(FileSystem.isValidFilename("file123.txt"))
        assertTrue(FileSystem.isValidFilename("123.txt"))
        assertTrue(FileSystem.isValidFilename("2023-report.pdf"))
    }

    @Test
    fun `test valid filename with Unicode characters`() {
        assertTrue(FileSystem.isValidFilename("文件.txt"))
        assertTrue(FileSystem.isValidFilename("файл.txt"))
        assertTrue(FileSystem.isValidFilename("αρχείο.txt"))
    }

    @Test
    fun `test invalid filename with backslash`() {
        assertFalse(FileSystem.isValidFilename("path\\to\\file.txt"))
        assertFalse(FileSystem.isValidFilename("file\\name.txt"))
    }

    @Test
    fun `test invalid filename with colon`() {
        assertFalse(FileSystem.isValidFilename("C:file.txt"))
        assertFalse(FileSystem.isValidFilename("file:name.txt"))
    }

    @Test
    fun `test invalid filename with asterisk`() {
        assertFalse(FileSystem.isValidFilename("*.txt"))
        assertFalse(FileSystem.isValidFilename("file*.txt"))
    }

    @Test
    fun `test invalid filename with question mark`() {
        assertFalse(FileSystem.isValidFilename("file?.txt"))
        assertFalse(FileSystem.isValidFilename("what??.txt"))
    }

    @Test
    fun `test invalid filename with double quote`() {
        assertFalse(FileSystem.isValidFilename("file\".txt"))
        assertFalse(FileSystem.isValidFilename("\"quoted\".txt"))
    }

    @Test
    fun `test invalid filename with angle brackets`() {
        assertFalse(FileSystem.isValidFilename("file<name>.txt"))
        assertFalse(FileSystem.isValidFilename("<file>.txt"))
        assertFalse(FileSystem.isValidFilename("file>.txt"))
    }

    @Test
    fun `test invalid filename with pipe`() {
        assertFalse(FileSystem.isValidFilename("file|name.txt"))
        assertFalse(FileSystem.isValidFilename("file|.txt"))
    }

    @Test
    fun `test validatePath with valid paths`() {
        assertEquals("file.txt", FileSystem.validatePath("file.txt"))
        assertEquals("dir/file.txt", FileSystem.validatePath("dir/file.txt"))
        assertEquals("path/to/file.txt", FileSystem.validatePath("path/to/file.txt"))
    }

    @Test
    fun `test validatePath with valid nested paths`() {
        assertEquals("a/b/c/d/file.txt", FileSystem.validatePath("a/b/c/d/file.txt"))
        assertEquals("deeply/nested/directory/structure/file.dat",
            FileSystem.validatePath("deeply/nested/directory/structure/file.dat"))
    }

    @Test
    fun `test validatePath throws on invalid characters`() {
        assertThrows<IOException> {
            FileSystem.validatePath("invalid:path.txt")
        }

        assertThrows<IOException> {
            FileSystem.validatePath("invalid*path.txt")
        }

        assertThrows<IOException> {
            FileSystem.validatePath("invalid?path.txt")
        }

        assertThrows<IOException> {
            FileSystem.validatePath("invalid\"path.txt")
        }

        assertThrows<IOException> {
            FileSystem.validatePath("invalid<path>.txt")
        }

        assertThrows<IOException> {
            FileSystem.validatePath("invalid|path.txt")
        }

        assertThrows<IOException> {
            FileSystem.validatePath("invalid\\path.txt")
        }
    }

    @Test
    fun `test validatePath with directory paths`() {
        assertEquals("dir/", FileSystem.validatePath("dir/"))
        assertEquals("path/to/dir/", FileSystem.validatePath("path/to/dir/"))
    }

    @Test
    fun `test validatePath with empty path`() {
        assertEquals("", FileSystem.validatePath(""))
    }

    @Test
    fun `test validatePath with root path`() {
        assertEquals("/", FileSystem.validatePath("/"))
    }

    @Test
    fun `test validatePath exception message contains useful info`() {
        val exception = assertThrows<IOException> {
            FileSystem.validatePath("bad*file.txt")
        }
        assertTrue(exception.message?.contains("invalid") ?: false)
    }

    @Test
    fun `test validatePath with paths containing dots`() {
        assertEquals("../file.txt", FileSystem.validatePath("../file.txt"))
        assertEquals("./file.txt", FileSystem.validatePath("./file.txt"))
        assertEquals(".hidden", FileSystem.validatePath(".hidden"))
        assertEquals("...", FileSystem.validatePath("..."))
    }

    @Test
    fun `test validatePath preserves forward slashes`() {
        val path = "dir1/dir2/dir3/file.txt"
        assertEquals(path, FileSystem.validatePath(path))
    }

    @Test
    fun `test isValidFilename with empty string`() {
        assertTrue(FileSystem.isValidFilename(""))
    }

    @Test
    fun `test isValidFilename with only extension`() {
        assertTrue(FileSystem.isValidFilename(".txt"))
        assertTrue(FileSystem.isValidFilename(".gitignore"))
    }

    @Test
    fun `test isValidFilename with multiple dots`() {
        assertTrue(FileSystem.isValidFilename("file.tar.gz"))
        assertTrue(FileSystem.isValidFilename("archive.tar.bz2"))
    }

    @Test
    fun `test isValidFilename with special but valid characters`() {
        assertTrue(FileSystem.isValidFilename("file+name.txt"))
        assertTrue(FileSystem.isValidFilename("file&name.txt"))
        assertTrue(FileSystem.isValidFilename("file\$name.txt"))
        assertTrue(FileSystem.isValidFilename("file#name.txt"))
        assertTrue(FileSystem.isValidFilename("file@name.txt"))
        assertTrue(FileSystem.isValidFilename("file!name.txt"))
        assertTrue(FileSystem.isValidFilename("file~name.txt"))
    }

    @Test
    fun `test path normalization with multiple slashes`() {
        // The filesystem should handle paths with multiple slashes
        // This test verifies that validatePath itself doesn't fail
        assertEquals("path//to//file.txt", FileSystem.validatePath("path//to//file.txt"))
    }

    @Test
    fun `test long filenames are valid`() {
        val longName = "a".repeat(255)
        assertTrue(FileSystem.isValidFilename(longName))
    }

    @Test
    fun `test very long paths are valid`() {
        val longPath = "a/".repeat(50) + "file.txt"
        assertEquals(longPath, FileSystem.validatePath(longPath))
    }

    @Test
    fun `test all invalid characters are rejected`() {
        val invalidChars = listOf('\\', ':', '*', '?', '"', '<', '>', '|')

        for (char in invalidChars) {
            assertFalse(FileSystem.isValidFilename("file${char}name.txt"),
                "Character '$char' should be invalid")
        }
    }

    @Test
    fun `test path with only slashes`() {
        assertEquals("/", FileSystem.validatePath("/"))
        assertEquals("//", FileSystem.validatePath("//"))
        assertEquals("///", FileSystem.validatePath("///"))
    }

    @Test
    fun `test filename with parentheses`() {
        assertTrue(FileSystem.isValidFilename("file(1).txt"))
        assertTrue(FileSystem.isValidFilename("(test).txt"))
    }

    @Test
    fun `test filename with brackets`() {
        assertTrue(FileSystem.isValidFilename("file[1].txt"))
        assertTrue(FileSystem.isValidFilename("[test].txt"))
    }

    @Test
    fun `test filename with braces`() {
        assertTrue(FileSystem.isValidFilename("file{1}.txt"))
        assertTrue(FileSystem.isValidFilename("{test}.txt"))
    }

    @Test
    fun `test filename with equals sign`() {
        assertTrue(FileSystem.isValidFilename("key=value.txt"))
    }

    @Test
    fun `test filename with comma and semicolon`() {
        assertTrue(FileSystem.isValidFilename("one,two,three.txt"))
        assertTrue(FileSystem.isValidFilename("list;of;items.txt"))
    }

    @Test
    fun `test path case sensitivity`() {
        // The path validation itself doesn't care about case
        assertEquals("File.txt", FileSystem.validatePath("File.txt"))
        assertEquals("file.txt", FileSystem.validatePath("file.txt"))
        assertEquals("FILE.TXT", FileSystem.validatePath("FILE.TXT"))
    }
}
