package li.cil.oc.server.fs

import li.cil.oc.Settings
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for NBT serialization and persistence of filesystem state.
 * This ensures that filesystems can be properly saved and loaded across game saves.
 */
class NBTPersistenceTest {

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
    fun `test empty filesystem persists correctly`() {
        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        // Empty filesystem should list nothing at root
        val listing = loaded.list("")
        assertTrue(listing.isNullOrEmpty())
    }

    @Test
    fun `test single file persists`() {
        val content = "Test content"
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)!!.write(content.toByteArray())
        fs.getHandle(handle)?.close()

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertTrue(loaded.exists("test.txt"))
        assertEquals(content.length.toLong(), loaded.size("test.txt"))

        val readHandle = loaded.open("test.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
        loaded.getHandle(readHandle)?.close()

        assertEquals(content, String(buffer, 0, bytesRead))
    }

    @Test
    fun `test multiple files persist`() {
        val files = mapOf(
            "file1.txt" to "Content 1",
            "file2.txt" to "Content 2",
            "file3.txt" to "Content 3"
        )

        for ((filename, content) in files) {
            val handle = fs.open(filename, Mode.Write)
            fs.getHandle(handle)!!.write(content.toByteArray())
            fs.getHandle(handle)?.close()
        }

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        for ((filename, expectedContent) in files) {
            assertTrue(loaded.exists(filename), "File $filename should exist")

            val readHandle = loaded.open(filename, Mode.Read)
            val buffer = ByteArray(100)
            val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
            loaded.getHandle(readHandle)?.close()

            assertEquals(expectedContent, String(buffer, 0, bytesRead))
        }
    }

    @Test
    fun `test directory structure persists`() {
        fs.makeDirectory("dir1")
        fs.makeDirectory("dir1/dir2")
        fs.makeDirectory("dir1/dir3")
        fs.makeDirectory("dir4")

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertTrue(loaded.exists("dir1/"))
        assertTrue(loaded.isDirectory("dir1/"))
        assertTrue(loaded.exists("dir1/dir2/"))
        assertTrue(loaded.exists("dir1/dir3/"))
        assertTrue(loaded.exists("dir4/"))
    }

    @Test
    fun `test nested files in directories persist`() {
        fs.makeDirectory("documents")
        fs.makeDirectory("documents/2023")

        val handle1 = fs.open("documents/readme.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("Documentation".toByteArray())
        fs.getHandle(handle1)?.close()

        val handle2 = fs.open("documents/2023/report.txt", Mode.Write)
        fs.getHandle(handle2)!!.write("Annual Report".toByteArray())
        fs.getHandle(handle2)?.close()

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertTrue(loaded.exists("documents/"))
        assertTrue(loaded.exists("documents/2023/"))
        assertTrue(loaded.exists("documents/readme.txt"))
        assertTrue(loaded.exists("documents/2023/report.txt"))

        val readHandle = loaded.open("documents/2023/report.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
        loaded.getHandle(readHandle)?.close()

        assertEquals("Annual Report", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test timestamps persist`() {
        val customTime = 123456789L

        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        fs.setLastModified("test.txt", customTime)

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertEquals(customTime, loaded.lastModified("test.txt"))
    }

    @Test
    fun `test directory timestamps persist`() {
        fs.makeDirectory("testdir")

        val customTime = 987654321L
        fs.setLastModified("testdir/", customTime)

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertEquals(customTime, loaded.lastModified("testdir/"))
    }

    @Test
    fun `test binary data persists correctly`() {
        val binaryData = ByteArray(256) { it.toByte() }

        val handle = fs.open("binary.dat", Mode.Write)
        fs.getHandle(handle)!!.write(binaryData)
        fs.getHandle(handle)?.close()

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        val readHandle = loaded.open("binary.dat", Mode.Read)
        val buffer = ByteArray(256)
        val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
        loaded.getHandle(readHandle)?.close()

        assertEquals(256, bytesRead)
        assertArrayEquals(binaryData, buffer)
    }

    @Test
    fun `test large file persists correctly`() {
        val largeData = ByteArray(10000) { (it % 256).toByte() }

        val handle = fs.open("large.dat", Mode.Write)
        fs.getHandle(handle)!!.write(largeData)
        fs.getHandle(handle)?.close()

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertEquals(largeData.size.toLong(), loaded.size("large.dat"))

        val readHandle = loaded.open("large.dat", Mode.Read)
        val buffer = ByteArray(10000)
        val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
        loaded.getHandle(readHandle)?.close()

        assertEquals(largeData.size, bytesRead)
        assertArrayEquals(largeData, buffer)
    }

    @Test
    fun `test empty files persist`() {
        val handle = fs.open("empty.txt", Mode.Write)
        fs.getHandle(handle)?.close()

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertTrue(loaded.exists("empty.txt"))
        assertEquals(0L, loaded.size("empty.txt"))
    }

    @Test
    fun `test open file handles persist`() {
        // Create a file and leave a handle open
        val handle = fs.open("test.txt", Mode.Write)
        fs.getHandle(handle)!!.write("Initial content".toByteArray())
        // Don't close the handle

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        // File should exist with the written content
        assertTrue(loaded.exists("test.txt"))

        val readHandle = loaded.open("test.txt", Mode.Read)
        val buffer = ByteArray(100)
        val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
        loaded.getHandle(readHandle)?.close()

        assertEquals("Initial content", String(buffer, 0, bytesRead))
    }

    @Test
    fun `test complex filesystem structure persists`() {
        // Create a complex directory structure
        fs.makeDirectory("root")
        fs.makeDirectory("root/subfolder1")
        fs.makeDirectory("root/subfolder2")
        fs.makeDirectory("root/subfolder1/deep")

        // Create files at various levels
        val files = mapOf(
            "root/file1.txt" to "Root level file",
            "root/subfolder1/file2.txt" to "Subfolder 1 file",
            "root/subfolder2/file3.txt" to "Subfolder 2 file",
            "root/subfolder1/deep/file4.txt" to "Deeply nested file"
        )

        for ((path, content) in files) {
            val handle = fs.open(path, Mode.Write)
            fs.getHandle(handle)!!.write(content.toByteArray())
            fs.getHandle(handle)?.close()
        }

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        // Verify all directories exist
        assertTrue(loaded.exists("root/"))
        assertTrue(loaded.exists("root/subfolder1/"))
        assertTrue(loaded.exists("root/subfolder2/"))
        assertTrue(loaded.exists("root/subfolder1/deep/"))

        // Verify all files and their content
        for ((path, expectedContent) in files) {
            assertTrue(loaded.exists(path))

            val readHandle = loaded.open(path, Mode.Read)
            val buffer = ByteArray(100)
            val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
            loaded.getHandle(readHandle)?.close()

            assertEquals(expectedContent, String(buffer, 0, bytesRead))
        }
    }

    @Test
    fun `test multiple save and load cycles`() {
        // Initial data
        val handle1 = fs.open("file1.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("Version 1".toByteArray())
        fs.getHandle(handle1)?.close()

        // First save/load cycle
        val nbt1 = NBTTagCompound()
        fs.save(nbt1)

        val fs2 = TestVirtualFileSystem()
        fs2.load(nbt1)

        // Modify and save again
        val handle2 = fs2.open("file2.txt", Mode.Write)
        fs2.getHandle(handle2)!!.write("Version 2".toByteArray())
        fs2.getHandle(handle2)?.close()

        val nbt2 = NBTTagCompound()
        fs2.save(nbt2)

        val fs3 = TestVirtualFileSystem()
        fs3.load(nbt2)

        // Verify both files exist
        assertTrue(fs3.exists("file1.txt"))
        assertTrue(fs3.exists("file2.txt"))

        // Verify contents
        val read1 = fs3.open("file1.txt", Mode.Read)
        val buffer1 = ByteArray(100)
        val bytes1 = fs3.getHandle(read1)!!.read(buffer1)
        assertEquals("Version 1", String(buffer1, 0, bytes1))
        fs3.getHandle(read1)?.close()

        val read2 = fs3.open("file2.txt", Mode.Read)
        val buffer2 = ByteArray(100)
        val bytes2 = fs3.getHandle(read2)!!.read(buffer2)
        assertEquals("Version 2", String(buffer2, 0, bytes2))
        fs3.getHandle(read2)?.close()
    }

    @Test
    fun `test special characters in filenames persist`() {
        val filenames = listOf(
            "file with spaces.txt",
            "file_with_underscores.txt",
            "file-with-dashes.txt",
            "file.multiple.dots.txt",
            "文件.txt"
        )

        for (filename in filenames) {
            val handle = fs.open(filename, Mode.Write)
            fs.getHandle(handle)!!.write(filename.toByteArray())
            fs.getHandle(handle)?.close()
        }

        val nbt = NBTTagCompound()
        fs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        for (filename in filenames) {
            assertTrue(loaded.exists(filename), "File '$filename' should exist")

            val readHandle = loaded.open(filename, Mode.Read)
            val buffer = ByteArray(200)
            val bytesRead = loaded.getHandle(readHandle)!!.read(buffer)
            loaded.getHandle(readHandle)?.close()

            assertEquals(filename, String(buffer, 0, bytesRead))
        }
    }

    @Test
    fun `test Capacity wrapper persists space accounting`() {
        val baseFs = TestVirtualFileSystem()
        val capacityFs = Capacity(baseFs, 2048)

        val handle = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(500))
        capacityFs.getHandle(handle)?.close()

        val usedBefore = capacityFs.spaceUsed()

        val nbt = NBTTagCompound()
        capacityFs.save(nbt)

        // Verify that capacity info was saved
        assertTrue(nbt.hasKey("capacity.used"))
        assertEquals(usedBefore, nbt.getLong("capacity.used"))
    }

    @Test
    fun `test ReadOnlyWrapper saves underlying filesystem`() {
        val baseFs = TestVirtualFileSystem()

        val handle = baseFs.open("test.txt", Mode.Write)
        baseFs.getHandle(handle)!!.write("Content".toByteArray())
        baseFs.getHandle(handle)?.close()

        val readOnlyFs = ReadOnlyWrapper(baseFs)

        val nbt = NBTTagCompound()
        readOnlyFs.save(nbt)

        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertTrue(loaded.exists("test.txt"))
    }

    @Test
    fun `test filesystem survives modifications between save and load`() {
        // Create initial state
        val handle1 = fs.open("file1.txt", Mode.Write)
        fs.getHandle(handle1)!!.write("Keep this".toByteArray())
        fs.getHandle(handle1)?.close()

        val handle2 = fs.open("file2.txt", Mode.Write)
        fs.getHandle(handle2)!!.write("Delete this".toByteArray())
        fs.getHandle(handle2)?.close()

        // Save
        val nbt = NBTTagCompound()
        fs.save(nbt)

        // Modify filesystem after save
        fs.delete("file2.txt")

        val handle3 = fs.open("file3.txt", Mode.Write)
        fs.getHandle(handle3)!!.write("New file".toByteArray())
        fs.getHandle(handle3)?.close()

        // Load should restore to saved state
        val loaded = TestVirtualFileSystem()
        loaded.load(nbt)

        assertTrue(loaded.exists("file1.txt"))
        assertTrue(loaded.exists("file2.txt")) // Should exist in loaded version
        assertFalse(loaded.exists("file3.txt")) // Should not exist in loaded version
    }

    // Test helper class
    private class TestVirtualFileSystem : VirtualFileSystem() {
        override fun segments(path: String): List<String> =
            FileSystem.validatePath(path).split("/").filter { it.isNotEmpty() }
    }
}
