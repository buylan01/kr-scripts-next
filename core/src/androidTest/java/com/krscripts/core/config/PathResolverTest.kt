package com.krscripts.core.config

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PathResolverTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var privateDir: File
    private lateinit var cacheDir: File

    companion object {
        private const val DISK_TEST_FILE = "test_disk"
        private const val PERFIX_ASSETS = "file:///android_asset/"
    }

    @Before
    fun setUp() {
        privateDir = context.filesDir
        cacheDir = context.cacheDir

        File(privateDir, DISK_TEST_FILE).writeText("disk file content")
        File(privateDir, "nested").mkdirs()
        File(privateDir, "nested/nested_file.txt").writeText("nested disk file")
        File(cacheDir, "cache_test.txt").writeText("cache file content")
    }

    @After
    fun tearDown() {
        File(privateDir, DISK_TEST_FILE).delete()
        File(privateDir, "nested/nested_file.txt").delete()
        File(privateDir, "nested").delete()
        File(cacheDir, "cache_test.txt").delete()
    }

    @Test
    fun resolvePath_emptyPath_returnsNull() {
        assertNull(PathResolver(context).resolvePath(""))
    }

    @Test
    fun resolvePath_assetsPrefix_validFile_returnsStream() {
        val resolver = PathResolver(context)
        val resolved = resolver.resolvePath("${PERFIX_ASSETS}test_asset")
        assertNotNull(resolved)
        val content = resolved?.inputStream?.bufferedReader()?.readText()?.trim()
        assertEquals("Android", content)
    }

    @Test
    fun resolvePath_assetsPrefix_invalidFile_returnsNull() {
        val resolver = PathResolver(context)
        assertNull(resolver.resolvePath("${PERFIX_ASSETS}non_existent"))
    }

    @Test
    fun resolvePath_assetsPrefix_withDotSegments_returnsStream() {
        val resolver = PathResolver(context)
        val resolved = resolver.resolvePath("$PERFIX_ASSETS./test_asset")
        assertNotNull(resolved)
        val content = resolved?.inputStream?.bufferedReader()?.readText()?.trim()
        assertEquals("Android", content)
    }

    @Test
    fun resolvePath_absoluteDiskPath_readableFile_returnsStream() {
        val file = File(privateDir, DISK_TEST_FILE)
        val resolver = PathResolver(context)
        val resolved = resolver.resolvePath(file.absolutePath)
        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved?.absolutePath)
        val content = resolved?.inputStream?.bufferedReader()?.readText()
        assertEquals("disk file content", content)
    }

    @Test
    fun resolvePath_absoluteDiskPath_unreadableFile_returnsNull() {
        val resolver = PathResolver(context)
        assertNull(resolver.resolvePath("/system/build.prop"))
    }

    @Test
    fun resolvePath_relativePath_noParent_checksPrivateDirFirst() {
        val resolver = PathResolver(context)
        val resolved = resolver.resolvePath(DISK_TEST_FILE)
        assertNotNull(resolved)
        // 应指向私有目录下的文件
        val expectedPath = File(privateDir, DISK_TEST_FILE).absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
        val content = resolved?.inputStream?.bufferedReader()?.readText()
        assertEquals("disk file content", content)
    }

    @Test
    fun resolvePath_relativePath_noParent_fileNotInPrivateDir_returnsNull() {
        val resolver = PathResolver(context)
        assertNull(resolver.resolvePath("no_such_file.txt"))
    }

    @Test
    fun resolvePath_relativePath_parentAssetsPrefix_returnsAssetsFile() {
        val resolver = PathResolver(context, PERFIX_ASSETS)
        val resolved = resolver.resolvePath("test_asset")
        assertNotNull(resolved)
        val content = resolved?.inputStream?.bufferedReader()?.readText()?.trim()
        assertEquals("Android", content)
    }

    @Test
    fun resolvePath_relativePath_parentAssetsPrefix_invalidFile_returnsNull() {
        val resolver = PathResolver(context, PERFIX_ASSETS)
        assertNull(resolver.resolvePath("non_existent.txt"))
    }

    @Test
    fun resolvePath_relativePath_parentAbsoluteDiskPath_returnsDiskFile() {
        val resolver = PathResolver(context, privateDir.absolutePath)
        val resolved = resolver.resolvePath(DISK_TEST_FILE)
        assertNotNull(resolved)
        val expectedPath = File(privateDir, DISK_TEST_FILE).absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
    }

    @Test
    fun resolvePath_relativePath_parentAbsoluteDiskPath_withNestedFile_returnsDiskFile() {
        val resolver = PathResolver(context, privateDir.absolutePath)
        val resolved = resolver.resolvePath("nested/nested_file.txt")
        assertNotNull(resolved)
        val expectedPath = File(privateDir, "nested/nested_file.txt").absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
    }

    @Test
    fun resolvePath_withDotSegment_returnsNormalizedPath() {
        val resolver = PathResolver(context)
        val resolved = resolver.resolvePath("./$DISK_TEST_FILE")
        assertNotNull(resolved)
        val expectedPath = File(privateDir, DISK_TEST_FILE).absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
    }

    @Test
    fun resolvePath_withParentAndDotSegment_returnsNormalizedPath() {
        val resolver = PathResolver(context, privateDir.absolutePath)
        val resolved = resolver.resolvePath("./$DISK_TEST_FILE")
        assertNotNull(resolved)
        val expectedPath = File(privateDir, DISK_TEST_FILE).absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
    }

    @Test
    fun resolvePath_withParentAndDoubleDotSegment_returnsNormalizedPath() {
        val nestedDir = File(privateDir, "nested")
        val resolver = PathResolver(context, nestedDir.absolutePath)
        val resolved = resolver.resolvePath("../$DISK_TEST_FILE")
        assertNotNull(resolved)
        val expectedPath = File(privateDir, DISK_TEST_FILE).absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
    }

    @Test
    fun resolvePath_withParentAndComplexDotSegments_returnsNormalizedPath() {
        val nestedDir = File(privateDir, "nested")
        val resolver = PathResolver(context, nestedDir.absolutePath)
        val resolved = resolver.resolvePath("./../$DISK_TEST_FILE")
        assertNotNull(resolved)
        val expectedPath = File(privateDir, DISK_TEST_FILE).absolutePath
        assertEquals(expectedPath, resolved?.absolutePath)
    }

    @Test
    fun resolvePath_withDoubleDotSegmentGoingBeyondRoot_returnsNull() {
        val resolver = PathResolver(context)
        val resolved = resolver.resolvePath("../../system/build.prop")
        assertNull(resolved)
    }

    @Test
    fun resolvePath_absolutePathWithAssetsPrefixAndParentProvided_parentIgnored() {
        val resolver = PathResolver(context, privateDir.absolutePath)
        val resolved = resolver.resolvePath("${PERFIX_ASSETS}test_asset")
        assertNotNull(resolved)
        val content = resolved?.inputStream?.bufferedReader()?.readText()?.trim()
        assertEquals("Android", content)
    }

    @Test
    fun resolvePath_absoluteDiskPathWithParentProvided_parentIgnored() {
        val file = File(privateDir, DISK_TEST_FILE)
        val resolver = PathResolver(context, "/foo/bar")
        val resolved = resolver.resolvePath(file.absolutePath)
        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved?.absolutePath)
    }
}