package li.cil.oc.server.fs

import li.cil.oc.Settings
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException
import java.io.IOException

class VirtualFileSystemTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            Settings.defaultsForTesting()
        }
    }

    private lateinit var fs: TestVirtualFileSystem
    @BeforeEach
    fun setUp() {
        fs = TestVirtualFileSystem()
    }

    @Test
    fun `test create and check file exists`() {
        // Initially the file shouldn't exist
        assertFalse(fs.exists("test.txt"))

        // Create a file by opening it for write
        val handle = fs.open("test.txt", Mode.Write)
        assertTrue(handle > 0)

        // File should exist now
        assertTrue(fs.exists("test.txt"))

        // Close the handle
        fs.getHandle(handle)?.close()
    }

    @Test
    fun `test write and read file`() {
        // Write some data
        val handle = fs.open("test.txt", Mode.Write)
        val writeHandle = fs.getHandle(handle)!!
        val data = "Hello, World!".toByteArray()
        writeHandle.write(data)
        writeHandle.close()

        // Read it back
        val readHandle = fs.open("test.txt", Mode.Read)
        val readBuffer = ByteArray(100)
        val bytesRead = fs.getHandle(readHandle)!!.read(readBuffer)
        fs.getHandle(readHandle)?.close()

        assertEquals(data.size, bytesRead)
        assertArrayEquals(data, readBuffer.copyOfRange(0, bytesRead))
    }

    @Test
    fun `test append to file`() {
        // Write initial data
        val handle1 = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("Hello".toByteArray())
        fs.getHandle(handle1)?.close()

        // Append more data
        val handle2 = fs.open("test.txt", Mode.Append)
        fs.getHandle(handle2)!!.write(", World!".toByteArray())
        fs.getHandle(handle2)?.close()

        // Read entire file
        val readHandle = fs.open("test.txt", Mode.Read)
        val readBuffer = ByteArray(100)
        val bytesRead = fs.getHandle(readHandle)!!.read(readBuffer)
        fs.getHandle(readHandle)?.close()

        val expected = "Hello, World!".toByteArray()
        assertEquals(expected.size, bytesRead)
        assertArrayEquals(expected, readBuffer.copyOfRange(0, bytesRead))
    }

    @Test
    fun `test overwrite file with Write mode`() {
        // Write initial data
        val handle1 = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("Initial content".toByteArray())
        fs.getHandle(handle1)?.close()

        // Overwrite with new data
        val handle2 = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle2)!!.write("New".toByteArray())
        fs.getHandle(handle2)?.close()

        // Read file
        val readHandle = fs.open("test.txt", Mode.Read)
        val readBuffer = ByteArray(100)
        val bytesRead = fs.getHandle(readHandle)!!.read(readBuffer)
        fs.getHandle(readHandle)?.close()

        val expected = "New".toByteArray()
        assertEquals(expected.size, bytesRead)
        assertArrayEquals(expected, readBuffer.copyOfRange(0, bytesRead))
    }

    @Test
    fun `test create directory`() {
        assertFalse(fs.exists("mydir/"))

        assertTrue(fs.makeDirectory("mydir"))
        assertTrue(fs.exists("mydir/"))
        assertTrue(fs.isDirectory("mydir/"))
        assertTrue(fs.isDirectory("mydir"))
    }

    @Test
    fun `test create nested directories`() {
        assertTrue(fs.makeDirectory("dir1"))
        assertTrue(fs.makeDirectory("dir1/dir2"))
        assertTrue(fs.makeDirectory("dir1/dir2/dir3"))

        assertTrue(fs.exists("dir1/"))
        assertTrue(fs.exists("dir1/dir2/"))
        assertTrue(fs.exists("dir1/dir2/dir3/"))
        assertTrue(fs.isDirectory("dir1/dir2/dir3/"))
    }

    @Test
    fun `test create file in directory`() {
        fs.makeDirectory("mydir")

        val handle = fs.open("mydir/file.txt", Mode.Write)
        fs.getHandle(handle)!!.write("content".toByteArray())
        fs.getHandle(handle)?.close()

        assertTrue(fs.exists("mydir/file.txt"))
        assertFalse(fs.isDirectory("mydir/file.txt"))
    }

    @Test
    fun `test list directory`() {
        fs.makeDirectory("dir1")

        val handle1 = fs.open("dir1/file1.txt", Mode.Write)
        fs.getHandle(handle1)?.close()

        val handle2 = fs.open("dir1/file2.txt", Mode.Write)
        fs.getHandle(handle2)?.close()

        fs.makeDirectory("dir1/subdir")

        val listing = fs.list("dir1/")
        assertNotNull(listing)
        assertEquals(3, listing!!.size)
        assertTrue(listing.contains("file1.txt"))
        assertTrue(listing.contains("file2.txt"))
        assertTrue(listing.contains("subdir/"))
    }

    @Test
    fun `test delete file`() {
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        assertTrue(fs.exists("test.txt"))
        assertTrue(fs.delete("test.txt"))
        assertFalse(fs.exists("test.txt"))
    }

    @Test
    fun `test delete empty directory`() {
        fs.makeDirectory("emptydir")

        assertTrue(fs.exists("emptydir/"))
        assertTrue(fs.delete("emptydir/"))
        assertFalse(fs.exists("emptydir/"))
    }

    @Test
    fun `test cannot delete non-empty directory`() {
        fs.makeDirectory("dir")
        val handle = fs.open("dir/file.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        assertFalse(fs.delete("dir/"))
        assertTrue(fs.exists("dir/"))
    }

    @Test
    fun `test cannot delete file with open handle`() {
        val handle = fs.open("test.txt", Mode.Write)

        assertTrue(fs.exists("test.txt"))
        assertFalse(fs.delete("test.txt"))

        fs.getHandle(handle)?.close()
        assertTrue(fs.delete("test.txt"))
    }

    @Test
    fun `test rename file`() {
        val handle = fs.open("old.txt", Mode.Write)
        fs.getHandle(handle)!!.write("content".toByteArray())
        fs.getHandle(handle)?.close()

        assertTrue(fs.rename("old.txt", "new.txt"))
        assertFalse(fs.exists("old.txt"))
        assertTrue(fs.exists("new.txt"))

        // Verify content is preserved
        val readHandle = fs.open("new.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = fs.getHandle(readHandle)!!.read(buffer)
        fs.getHandle(readHandle)?.close()

        assertEquals("content", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test rename overwrites existing file`() {
        val handle1 = fs.open("file1.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("content1".toByteArray())
        fs.getHandle(handle1)?.close()

        val handle2 = fs.open("file2.txt", Mode.Write)
        fs.getHandle(handle2)!!.write("content2".toByteArray())
        fs.getHandle(handle2)?.close()

        assertTrue(fs.rename("file1.txt", "file2.txt"))
        assertFalse(fs.exists("file1.txt"))
        assertTrue(fs.exists("file2.txt"))

        // Verify file2 now has content from file1
        val readHandle = fs.open("file2.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = fs.getHandle(readHandle)!!.read(buffer)
        fs.getHandle(readHandle)?.close()

        assertEquals("content1", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test file size`() {
        val content = "Hello, World!"
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)!!.write(content.toByteArray())
        fs.getHandle(handle)?.close()

        assertEquals(content.length.toLong(), fs.size("test.txt"))
    }

    @Test
    fun `test directory size is zero`() {
        fs.makeDirectory("dir")
        assertEquals(0L, fs.size("dir/"))
    }

    @Test
    fun `test last modified timestamp`() {
        val beforeCreate = System.currentTimeMillis()
        Thread.sleep(10) // Small delay to ensure timestamp difference

        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        val timestamp = fs.lastModified("test.txt")
        assertTrue(timestamp >= beforeCreate)
        assertTrue(timestamp <= System.currentTimeMillis())
    }

    @Test
    fun `test set last modified`() {
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        val customTime = 1000000L
        assertTrue(fs.setLastModified("test.txt", customTime))
        assertEquals(customTime, fs.lastModified("test.txt"))
    }

    @Test
    fun `test cannot set negative last modified`() {
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        assertFalse(fs.setLastModified("test.txt", -1))
    }

    @Test
    fun `test seek in file`() {
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)!!.write("0123456789".toByteArray())
        fs.getHandle(handle)?.close()

        val readHandle = fs.open("test.txt", Mode.Read)
        val h = fs.getHandle(readHandle)!!

        // Seek to position 5
        assertEquals(5L, h.seek(5))

        val buffer = ByteArray(3)
        val bytesRead = h.read(buffer)
        assertEquals(3, bytesRead)
        assertEquals("567", String(buffer))

        h.close()
    }

    @Test
    fun `test invalid path characters throw exception`() {
        assertThrows<IOException> {
            fs.open("invalid:file.txt", Mode.Write)
        }

        assertThrows<IOException> {
            fs.open("invalid*file.txt", Mode.Write)
        }

        assertThrows<IOException> {
            fs.open("invalid?file.txt", Mode.Write)
        }
    }

    @Test
    fun `test cannot open directory for reading`() {
        fs.makeDirectory("dir")

        assertThrows<FileNotFoundException> {
            fs.open("dir/", Mode.Read)
        }
    }

    @Test
    fun `test cannot open directory for writing`() {
        fs.makeDirectory("dir")

        assertThrows<FileNotFoundException> {
            fs.open("dir/", Mode.Write)
        }
    }

    @Test
    fun `test space reporting`() {
        // VirtualFileSystem returns -1 for space used and total
        assertEquals(-1L, fs.spaceUsed())
        assertEquals(-1L, fs.spaceTotal())
    }

    @Test
    fun `test close filesystem`() {
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)!!.write("content".toByteArray())

        // Don't close the handle before closing the filesystem
        fs.close()

        // After closing, handles should be invalid
        assertNull(fs.getHandle(handle))
    }

    @Test
    fun `test NBT save and load`() {
        // Create some files and directories
        fs.makeDirectory("dir1")

        val handle1 = fs.open("file1.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("content1".toByteArray())
        fs.getHandle(handle1)?.close()

        val handle2 = fs.open("dir1/file2.txt", Mode.Write)
        fs.getHandle(handle2)!!.write("content2".toByteArray())
        fs.getHandle(handle2)?.close()

        // Save to NBT
        val nbt = NBTTagCompound()
        fs.save(nbt)

        // Create a new filesystem and load from NBT
        val newFs = TestVirtualFileSystem()
        newFs.load(nbt)

        // Verify structure was restored
        assertTrue(newFs.exists("file1.txt"))
        assertTrue(newFs.exists("dir1/"))
        assertTrue(newFs.exists("dir1/file2.txt"))

        // Verify content was restored
        val readHandle1 = newFs.open("file1.txt", Mode.Read)
        val buffer1 = ByteArray(100)
        val bytes1 = newFs.getHandle(readHandle1)!!.read(buffer1)
        assertEquals("content1", String(buffer1, 0, bytes1))
        newFs.getHandle(readHandle1)?.close()

        val readHandle2 = newFs.open("dir1/file2.txt", Mode.Read)
        val buffer2 = ByteArray(100)
        val bytes2 = newFs.getHandle(readHandle2)!!.read(buffer2)
        assertEquals("content2", String(buffer2, 0, bytes2))
        newFs.getHandle(readHandle2)?.close()
    }

    @Test
    fun `test empty filesystem NBT persistence`() {
        val nbt = NBTTagCompound()
        fs.save(nbt)

        val newFs = TestVirtualFileSystem()
        newFs.load(nbt)

        // Should be empty
        assertTrue(newFs.list("").isNullOrEmpty())
    }

    @Test
    fun `test write at arbitrary position`() {
        // Create file with initial content
        val handle = fs.open("test.txt", Mode.Write)
        val h = fs.getHandle(handle)!!
        h.write("0000000000".toByteArray())

        // Seek to middle and write
        h.seek(3)
        h.write("ABC".toByteArray())
        h.close()

        // Read back and verify
        val readHandle = fs.open("test.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = fs.getHandle(readHandle)!!.read(buffer)
        fs.getHandle(readHandle)?.close()

        assertEquals("000ABC0000", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test write extends file beyond current size`() {
        val handle = fs.open("test.txt", Mode.Write)
        val h = fs.getHandle(handle)!!

        // Write at position 0
        h.write("Start".toByteArray())

        // Seek beyond current size and write
        h.seek(20)
        h.write("End".toByteArray())
        h.close()

        // File should be extended with zeros
        assertEquals(23L, fs.size("test.txt"))
    }

    @Test
    fun `test multiple concurrent read handles`() {
        // Write test data
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)!!.write("0123456789".toByteArray())
        fs.getHandle(handle)?.close()

        // Open multiple read handles
        val readHandle1 = fs.open("test.txt", Mode.Read)
        val readHandle2 = fs.open("test.txt", Mode.Read)

        val h1 = fs.getHandle(readHandle1)!!
        val h2 = fs.getHandle(readHandle2)!!

        // Read from different positions
        h1.seek(0)
        val buffer1 = ByteArray(3)
        h1.read(buffer1)
        assertEquals("012", String(buffer1))

        h2.seek(5)
        val buffer2 = ByteArray(3)
        h2.read(buffer2)
        assertEquals("567", String(buffer2))

        h1.close()
        h2.close()
    }

    // Test helper class that exposes VirtualFileSystem for testing
    private class TestVirtualFileSystem : VirtualFileSystem() {
        override fun segments(path: String): List<String> =
            FileSystem.validatePath(path).split("/").filter { it.isNotEmpty() }
    }
}
