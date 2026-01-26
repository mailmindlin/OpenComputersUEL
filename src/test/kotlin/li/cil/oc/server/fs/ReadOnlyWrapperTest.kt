package li.cil.oc.server.fs

import li.cil.oc.api.fs.Mode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class ReadOnlyWrapperTest {
    private lateinit var baseFs: TestVirtualFileSystem
    private lateinit var readOnlyFs: ReadOnlyWrapper

    @BeforeEach
    fun setUp() {
        baseFs = TestVirtualFileSystem()

        // Populate the base filesystem with some test data
        baseFs.makeDirectory("dir1")

        val handle1 = baseFs.open("file1.txt", Mode.Write)
        baseFs.getHandle(handle1)!!.write("Content of file 1".toByteArray())
        baseFs.getHandle(handle1)?.close()

        val handle2 = baseFs.open("dir1/file2.txt", Mode.Write)
        baseFs.getHandle(handle2)!!.write("Content of file 2".toByteArray())
        baseFs.getHandle(handle2)?.close()

        // Wrap it as read-only
        readOnlyFs = ReadOnlyWrapper(baseFs)
    }

    @Test
    fun `test isReadOnly returns true`() {
        assertTrue(readOnlyFs.isReadOnly())
    }

    @Test
    fun `test can read existing file`() {
        val handle = readOnlyFs.open("file1.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = readOnlyFs.getHandle(handle)!!.read(buffer)
        readOnlyFs.getHandle(handle)?.close()

        assertEquals("Content of file 1", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test can read file in directory`() {
        val handle = readOnlyFs.open("dir1/file2.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = readOnlyFs.getHandle(handle)!!.read(buffer)
        readOnlyFs.getHandle(handle)?.close()

        assertEquals("Content of file 2", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test can check file exists`() {
        assertTrue(readOnlyFs.exists("file1.txt"))
        assertTrue(readOnlyFs.exists("dir1/"))
        assertTrue(readOnlyFs.exists("dir1/file2.txt"))
        assertFalse(readOnlyFs.exists("nonexistent.txt"))
    }

    @Test
    fun `test can check if path is directory`() {
        assertTrue(readOnlyFs.isDirectory("dir1/"))
        assertFalse(readOnlyFs.isDirectory("file1.txt"))
    }

    @Test
    fun `test can get file size`() {
        assertEquals(18L, readOnlyFs.size("file1.txt"))
        assertEquals(18L, readOnlyFs.size("dir1/file2.txt"))
    }

    @Test
    fun `test can get last modified time`() {
        val timestamp = readOnlyFs.lastModified("file1.txt")
        assertTrue(timestamp > 0)
    }

    @Test
    fun `test can list directory`() {
        val listing = readOnlyFs.list("dir1/")
        assertNotNull(listing)
        assertEquals(1, listing!!.size)
        assertTrue(listing.contains("file2.txt"))
    }

    @Test
    fun `test cannot open file for writing`() {
        assertThrows<FileNotFoundException> {
            readOnlyFs.open("file1.txt", Mode.Write)
        }
    }

    @Test
    fun `test cannot open file for appending`() {
        assertThrows<FileNotFoundException> {
            readOnlyFs.open("file1.txt", Mode.Append)
        }
    }

    @Test
    fun `test cannot open new file for writing`() {
        assertThrows<FileNotFoundException> {
            readOnlyFs.open("newfile.txt", Mode.Write)
        }
    }

    @Test
    fun `test cannot delete file`() {
        assertTrue(readOnlyFs.exists("file1.txt"))
        assertFalse(readOnlyFs.delete("file1.txt"))
        assertTrue(readOnlyFs.exists("file1.txt"))
    }

    @Test
    fun `test cannot delete directory`() {
        assertTrue(readOnlyFs.exists("dir1/"))
        assertFalse(readOnlyFs.delete("dir1/"))
        assertTrue(readOnlyFs.exists("dir1/"))
    }

    @Test
    fun `test cannot create directory`() {
        assertFalse(readOnlyFs.makeDirectory("newdir"))
        assertFalse(readOnlyFs.exists("newdir/"))
    }

    @Test
    fun `test cannot rename file`() {
        assertTrue(readOnlyFs.exists("file1.txt"))
        assertFalse(readOnlyFs.rename("file1.txt", "renamed.txt"))
        assertTrue(readOnlyFs.exists("file1.txt"))
        assertFalse(readOnlyFs.exists("renamed.txt"))
    }

    @Test
    fun `test cannot rename directory`() {
        assertTrue(readOnlyFs.exists("dir1/"))
        assertFalse(readOnlyFs.rename("dir1/", "renameddir/"))
        assertTrue(readOnlyFs.exists("dir1/"))
        assertFalse(readOnlyFs.exists("renameddir/"))
    }

    @Test
    fun `test cannot set last modified time`() {
        val originalTime = readOnlyFs.lastModified("file1.txt")
        assertFalse(readOnlyFs.setLastModified("file1.txt", 12345L))
        assertEquals(originalTime, readOnlyFs.lastModified("file1.txt"))
    }

    @Test
    fun `test space reporting is delegated`() {
        // VirtualFileSystem returns -1 for both
        assertEquals(-1L, readOnlyFs.spaceTotal())
        assertEquals(-1L, readOnlyFs.spaceUsed())
    }

    @Test
    fun `test can read file multiple times`() {
        for (i in 1..5) {
            val handle = readOnlyFs.open("file1.txt", Mode.Read)
            val buffer = ByteArray(100)
            val bytesRead = readOnlyFs.getHandle(handle)!!.read(buffer)
            readOnlyFs.getHandle(handle)?.close()

            assertEquals("Content of file 1", String(buffer, 0, bytesRead))
        }
    }

    @Test
    fun `test can have multiple concurrent read handles`() {
        val handle1 = readOnlyFs.open("file1.txt", Mode.Read)
        val handle2 = readOnlyFs.open("dir1/file2.txt", Mode.Read)

        val buffer1 = ByteArray(100)
        val bytes1 = readOnlyFs.getHandle(handle1)!!.read(buffer1)
        assertEquals("Content of file 1", String(buffer1, 0, bytes1))

        val buffer2 = ByteArray(100)
        val bytes2 = readOnlyFs.getHandle(handle2)!!.read(buffer2)
        assertEquals("Content of file 2", String(buffer2, 0, bytes2))

        readOnlyFs.getHandle(handle1)?.close()
        readOnlyFs.getHandle(handle2)?.close()
    }

    @Test
    fun `test seek operations work on read handles`() {
        val handle = readOnlyFs.open("file1.txt", Mode.Read)
        val h = readOnlyFs.getHandle(handle)!!

        h.seek(8)
        assertEquals(8L, h.position())

        val buffer = ByteArray(6)
        val bytesRead = h.read(buffer)
        assertEquals(6, bytesRead)
        assertEquals("f file", String(buffer))

        h.close()
    }

    @Test
    fun `test wrapping already read-only filesystem is idempotent`() {
        assertTrue(readOnlyFs.isReadOnly())

        val doubleWrapped = ReadOnlyWrapper(readOnlyFs)
        assertTrue(doubleWrapped.isReadOnly())

        // Should still be able to read
        val handle = doubleWrapped.open("file1.txt", Mode.Read)
        assertNotNull(doubleWrapped.getHandle(handle))
        doubleWrapped.getHandle(handle)?.close()

        // Should still not be able to write
        assertThrows<FileNotFoundException> {
            doubleWrapped.open("file1.txt", Mode.Write)
        }
    }

    @Test
    fun `test empty directory listing`() {
        // Create empty directory in base
        baseFs.makeDirectory("emptydir")

        val readOnlyFs2 = ReadOnlyWrapper(baseFs)
        val listing = readOnlyFs2.list("emptydir/")

        assertNotNull(listing)
        assertEquals(0, listing!!.size)
    }

    @Test
    fun `test reading from root directory`() {
        val rootListing = readOnlyFs.list("")
        assertNotNull(rootListing)
        assertTrue(rootListing!!.size >= 2) // At least file1.txt and dir1/
    }

    @Test
    fun `test file not found for non-existent file`() {
        assertThrows<FileNotFoundException> {
            readOnlyFs.open("nonexistent.txt", Mode.Read)
        }
    }

    @Test
    fun `test cannot open directory for reading`() {
        assertThrows<FileNotFoundException> {
            readOnlyFs.open("dir1/", Mode.Read)
        }
    }

    @Test
    fun `test modifications to base filesystem are visible`() {
        // Add a new file to base filesystem
        val handle = baseFs.open("newfile.txt", Mode.Write)
        baseFs.getHandle(handle)!!.write("New content".toByteArray())
        baseFs.getHandle(handle)?.close()

        // Should be visible through read-only wrapper
        assertTrue(readOnlyFs.exists("newfile.txt"))

        val readHandle = readOnlyFs.open("newfile.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = readOnlyFs.getHandle(readHandle)!!.read(buffer)
        readOnlyFs.getHandle(readHandle)?.close()

        assertEquals("New content", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test base filesystem modifications after deletion are visible`() {
        // Delete from base filesystem
        baseFs.delete("file1.txt")

        // Should no longer exist through read-only wrapper
        assertFalse(readOnlyFs.exists("file1.txt"))
    }

    // Test helper class
    private class TestVirtualFileSystem : VirtualFileSystem() {
        override fun segments(path: String): List<String> =
            FileSystem.validatePath(path).split("/").filter { it.isNotEmpty() }
    }
}
