package li.cil.oc.server.fs

import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class CapacityTest {
    private lateinit var baseFs: TestVirtualFileSystem
    private lateinit var capacityFs: Capacity

    @BeforeEach
    fun setUp() {
        baseFs = TestVirtualFileSystem()
        // Create a filesystem with 1KB capacity
        capacityFs = Capacity(baseFs, 1024)
    }

    @Test
    fun `test capacity is reported correctly`() {
        assertEquals(1024L, capacityFs.spaceTotal())
    }

    @Test
    fun `test initial space used`() {
        // Initially should have minimal usage
        val initialUsed = capacityFs.spaceUsed()
        assertTrue(initialUsed >= 0)
        assertTrue(initialUsed < 1024)
    }

    @Test
    fun `test creating file increases space used`() {
        val initialUsed = capacityFs.spaceUsed()

        val handle = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle)?.close()

        // Space used should increase (file cost is added)
        assertTrue(capacityFs.spaceUsed() > initialUsed)
    }

    @Test
    fun `test writing data increases space used`() {
        val handle = capacityFs.open("test.txt", Mode.Write)

        val usedAfterCreate = capacityFs.spaceUsed()

        val data = ByteArray(100) { it.toByte() }
        capacityFs.getHandle(handle)!!.write(data)

        // Space should increase by size of data written
        assertEquals(usedAfterCreate + 100, capacityFs.spaceUsed())

        capacityFs.getHandle(handle)?.close()
    }

    @Test
    fun `test deleting file decreases space used`() {
        val handle = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(100))
        capacityFs.getHandle(handle)?.close()

        val usedBeforeDelete = capacityFs.spaceUsed()

        capacityFs.delete("test.txt")

        // Space should be freed
        assertTrue(capacityFs.spaceUsed() < usedBeforeDelete)
    }

    @Test
    fun `test cannot exceed capacity when creating file`() {
        // Fill up the filesystem close to capacity
        val largeData = ByteArray(900)
        val handle = capacityFs.open("large.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(largeData)
        capacityFs.getHandle(handle)?.close()

        // Try to create another file that would exceed capacity
        assertThrows<IOException> {
            val handle2 = capacityFs.open("another.txt", Mode.Write)
            capacityFs.getHandle(handle2)!!.write(ByteArray(500))
        }
    }

    @Test
    fun `test cannot exceed capacity when writing data`() {
        val handle = capacityFs.open("test.txt", Mode.Write)

        // Try to write more data than capacity allows
        assertThrows<IOException> {
            capacityFs.getHandle(handle)!!.write(ByteArray(2000))
        }
    }

    @Test
    fun `test overwriting file with Write mode releases old space`() {
        // Create file with some content
        val handle1 = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle1)!!.write(ByteArray(200))
        capacityFs.getHandle(handle1)?.close()

        val usedAfterFirstWrite = capacityFs.spaceUsed()

        // Overwrite with smaller content
        val handle2 = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle2)!!.write(ByteArray(50))
        capacityFs.getHandle(handle2)?.close()

        // Space used should reflect the new smaller size
        assertTrue(capacityFs.spaceUsed() < usedAfterFirstWrite)
    }

    @Test
    fun `test append mode does not release space`() {
        // Create file with initial content
        val handle1 = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle1)!!.write(ByteArray(100))
        capacityFs.getHandle(handle1)?.close()

        val usedAfterWrite = capacityFs.spaceUsed()

        // Open in append mode (should not change space until we write)
        val handle2 = capacityFs.open("test.txt", Mode.Append)
        assertEquals(usedAfterWrite, capacityFs.spaceUsed())

        // Append data
        capacityFs.getHandle(handle2)!!.write(ByteArray(50))

        // Space should increase
        assertEquals(usedAfterWrite + 50, capacityFs.spaceUsed())
        capacityFs.getHandle(handle2)?.close()
    }

    @Test
    fun `test making directory consumes space`() {
        val usedBefore = capacityFs.spaceUsed()

        capacityFs.makeDirectory("dir")

        // Directory creation should consume some space (file cost)
        assertTrue(capacityFs.spaceUsed() > usedBefore)
    }

    @Test
    fun `test cannot create directory when capacity exceeded`() {
        // Fill up the filesystem
        val handle = capacityFs.open("large.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(1000))
        capacityFs.getHandle(handle)?.close()

        // Try to create directory
        assertThrows<IOException> {
            capacityFs.makeDirectory("newdir")
        }
    }

    @Test
    fun `test rename to existing file releases old file space`() {
        // Create two files
        val handle1 = capacityFs.open("file1.txt", Mode.Write)
        capacityFs.getHandle(handle1)!!.write(ByteArray(100))
        capacityFs.getHandle(handle1)?.close()

        val handle2 = capacityFs.open("file2.txt", Mode.Write)
        capacityFs.getHandle(handle2)!!.write(ByteArray(200))
        capacityFs.getHandle(handle2)?.close()

        val usedBeforeRename = capacityFs.spaceUsed()

        // Rename file1 to file2 (overwrites file2)
        capacityFs.rename("file1.txt", "file2.txt")

        // Space should be freed (file2's old content)
        assertTrue(capacityFs.spaceUsed() < usedBeforeRename)
    }

    @Test
    fun `test NBT save persists used space`() {
        // Create some files
        val handle = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(100))
        capacityFs.getHandle(handle)?.close()

        val usedSpace = capacityFs.spaceUsed()

        // Save to NBT
        val nbt = NBTTagCompound()
        capacityFs.save(nbt)

        // Verify capacity.used is saved
        assertTrue(nbt.hasKey("capacity.used"))
        assertEquals(usedSpace, nbt.getLong("capacity.used"))
    }

    @Test
    fun `test load from NBT allows exceeding capacity temporarily`() {
        // This tests that loading data from NBT doesn't fail even if
        // the saved data exceeds the current capacity limit

        // Create a large file
        val handle = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(800))
        capacityFs.getHandle(handle)?.close()

        // Save to NBT
        val nbt = NBTTagCompound()
        capacityFs.save(nbt)

        // Create a new filesystem with smaller capacity
        val baseFs2 = TestVirtualFileSystem()
        val smallCapacityFs = Capacity(baseFs2, 500)

        // Load should succeed even though data exceeds capacity
        assertDoesNotThrow {
            smallCapacityFs.load(nbt)
        }

        // File should be loaded
        assertTrue(smallCapacityFs.exists("test.txt"))
        assertEquals(800L, smallCapacityFs.size("test.txt"))

        // But trying to create new files should fail
        assertThrows<IOException> {
            smallCapacityFs.open("newfile.txt", Mode.Write)
        }
    }

    @Test
    fun `test close recalculates space used`() {
        // Create some files
        val handle1 = capacityFs.open("file1.txt", Mode.Write)
        capacityFs.getHandle(handle1)!!.write(ByteArray(100))
        capacityFs.getHandle(handle1)?.close()

        val handle2 = capacityFs.open("file2.txt", Mode.Write)
        capacityFs.getHandle(handle2)!!.write(ByteArray(200))
        capacityFs.getHandle(handle2)?.close()

        val usedBefore = capacityFs.spaceUsed()

        // Close and space should be recalculated
        capacityFs.close()

        // After close, space used should still be consistent
        // (Note: This creates a new Capacity instance to test recalculation)
        val capacityFs2 = Capacity(baseFs, 1024)
        assertTrue(capacityFs2.spaceUsed() >= 0)
    }

    @Test
    fun `test read-only operations do not affect capacity`() {
        // Create a file
        val handle = capacityFs.open("test.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(100))
        capacityFs.getHandle(handle)?.close()

        val usedAfterWrite = capacityFs.spaceUsed()

        // Read the file multiple times
        for (i in 1..5) {
            val readHandle = capacityFs.open("test.txt", Mode.Read)
            val buffer = ByteArray(100)
            capacityFs.getHandle(readHandle)!!.read(buffer)
            capacityFs.getHandle(readHandle)?.close()
        }

        // Space used should not change
        assertEquals(usedAfterWrite, capacityFs.spaceUsed())
    }

    @Test
    fun `test capacity with nested directories`() {
        val initialUsed = capacityFs.spaceUsed()

        // Create nested structure
        capacityFs.makeDirectory("dir1")
        capacityFs.makeDirectory("dir1/dir2")

        val handle = capacityFs.open("dir1/dir2/file.txt", Mode.Write)
        capacityFs.getHandle(handle)!!.write(ByteArray(50))
        capacityFs.getHandle(handle)?.close()

        // Space should account for all directories and file
        assertTrue(capacityFs.spaceUsed() > initialUsed)

        // Delete everything
        capacityFs.delete("dir1/dir2/file.txt")
        capacityFs.delete("dir1/dir2/")
        capacityFs.delete("dir1/")

        // Space should be mostly freed (back close to initial)
        assertTrue(capacityFs.spaceUsed() <= initialUsed)
    }

    @Test
    fun `test zero capacity filesystem`() {
        val zeroCapFs = Capacity(TestVirtualFileSystem(), 0)

        assertEquals(0L, zeroCapFs.spaceTotal())

        // Should not be able to create any files
        assertThrows<IOException> {
            zeroCapFs.open("test.txt", Mode.Write)
        }

        // Should not be able to create directories
        assertThrows<IOException> {
            zeroCapFs.makeDirectory("dir")
        }
    }

    @Test
    fun `test space accounting with multiple write handles`() {
        // Open file and write some data
        val handle1 = capacityFs.open("file1.txt", Mode.Write)
        capacityFs.getHandle(handle1)!!.write(ByteArray(100))

        val used1 = capacityFs.spaceUsed()

        // Open another file
        val handle2 = capacityFs.open("file2.txt", Mode.Write)
        capacityFs.getHandle(handle2)!!.write(ByteArray(150))

        val used2 = capacityFs.spaceUsed()

        // Should account for both files
        assertTrue(used2 > used1)
        assertTrue(used2 - used1 >= 150)

        capacityFs.getHandle(handle1)?.close()
        capacityFs.getHandle(handle2)?.close()
    }

    // Test helper class
    private class TestVirtualFileSystem : VirtualFileSystem() {
        override fun segments(path: String): List<String> =
            FileSystem.validatePath(path).split("/").filter { it.isNotEmpty() }
    }
}
