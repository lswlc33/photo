package com.lc33.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputTargetTest {

    @Test
    fun aResultGoesBackIntoTheFolderItsSourceCameFrom() {
        assertEquals(
            "DCIM/Camera",
            resolveOutputFolder("DCIM/Camera/", OutputKind.IMAGE),
        )
        assertEquals(
            "Movies/Recordings",
            resolveOutputFolder("Movies/Recordings/", OutputKind.VIDEO),
        )
    }

    @Test
    fun theTrailingSeparatorMediaStoreStoresIsDropped() {
        // MediaStore reports RELATIVE_PATH with a trailing slash and rejects an
        // insert that carries one, so the two forms cannot be passed straight
        // through to each other.
        assertEquals("Pictures", resolveOutputFolder("Pictures/", OutputKind.IMAGE))
        assertEquals("Pictures", resolveOutputFolder("Pictures", OutputKind.IMAGE))
        assertEquals("DCIM/Trip", resolveOutputFolder("DCIM//Trip//", OutputKind.IMAGE))
    }

    @Test
    fun aFolderTheCollectionWillNotTakeFallsBackToTheAppFolder() {
        // A video indexed under Download is a real case, and MediaStore refuses that
        // primary directory for the video collection.
        assertEquals(
            GalleryWriter.VIDEO_FOLDER,
            resolveOutputFolder("Download/Telegram/", OutputKind.VIDEO),
        )
        assertEquals(
            GalleryWriter.IMAGE_FOLDER,
            resolveOutputFolder("Movies/Clips/", OutputKind.IMAGE),
        )
    }

    @Test
    fun extractedAudioNeverStaysBesideTheVideoItCameFrom() {
        // The audio collection takes none of the picture directories, so audio always
        // relocates - which the review page says out loud rather than hiding.
        assertEquals(
            GalleryWriter.AUDIO_FOLDER,
            resolveOutputFolder("Movies/Clips/", OutputKind.AUDIO),
        )
        assertEquals("Music/Voice", resolveOutputFolder("Music/Voice/", OutputKind.AUDIO))
    }

    @Test
    fun aMissingOrUselessPathFallsBackRatherThanBuildingOne() {
        assertEquals(GalleryWriter.IMAGE_FOLDER, resolveOutputFolder(null, OutputKind.IMAGE))
        assertEquals(GalleryWriter.IMAGE_FOLDER, resolveOutputFolder("", OutputKind.IMAGE))
        assertEquals(GalleryWriter.IMAGE_FOLDER, resolveOutputFolder("///", OutputKind.IMAGE))
    }

    @Test
    fun aPathThatTriesToEscapeIsRefusedRatherThanEdited() {
        // `..` cannot appear in a RELATIVE_PATH MediaStore reported, so this is the
        // guard for a crafted value - and editing one into something writable is
        // worse than declining to use it.
        assertEquals(
            GalleryWriter.IMAGE_FOLDER,
            resolveOutputFolder("DCIM/../../secret/", OutputKind.IMAGE),
        )
        assertEquals(GalleryWriter.IMAGE_FOLDER, resolveOutputFolder("../..", OutputKind.IMAGE))
        // A `.` is only noise, so it is dropped and the rest still counts.
        assertEquals("DCIM/Camera", resolveOutputFolder("./DCIM/Camera", OutputKind.IMAGE))
    }

    @Test
    fun theCheckIsOnTheDirectoryNameNotItsCase() {
        assertEquals("dcim/Camera", resolveOutputFolder("dcim/Camera/", OutputKind.IMAGE))
        assertEquals("PICTURES/Saved", resolveOutputFolder("PICTURES/Saved/", OutputKind.IMAGE))
    }

    @Test
    fun normalizingKeepsTheSourceFolderComparableWithTheChosenOne() {
        // What tells "went back where it came from" apart from "was relocated".
        assertEquals("DCIM/Camera", normalizeFolder("DCIM/Camera/"))
        assertEquals(null, normalizeFolder(null))
        assertEquals(null, normalizeFolder("/"))
    }
}
