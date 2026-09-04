package com.lc33.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clustering half of [PerceptualHash], which is where a mistake costs a photo: the
 * similar list offers a one-tap "keep one copy", so every group here is a candidate for
 * deletion.
 */
class SimilarGroupingTest {

    /**
     * Single-link union-find computes connected components, which have no diameter bound:
     * twelve pairwise five-bit hops put two photos sixty bits apart in one group, and
     * `MinFeatureBits` does not help because it only filters the popcount extremes.
     */
    @Test
    fun aChainOfNeighboursDoesNotCollapseIntoOneGroup() {
        // Each step flips 4 bits from the previous one, so consecutive items are within
        // the threshold but the ends are 32 bits apart.
        val items = (0 until 9).map { step ->
            Item(id = step, hash = chainedHash(step, bitsPerStep = 4))
        }

        val groups = groupSimilarItems(items, hashOf = { it.hash }, maxDistance = 5)

        assertTrue("no group may span the whole chain", groups.all { it.size < items.size })
        groups.forEach { group ->
            val representative = requireNotNull(group.first().hash)
            group.forEach { member ->
                val distance = PerceptualHash.distance(representative, requireNotNull(member.hash))
                assertTrue("member ${member.id} is $distance bits out", distance <= 5)
            }
        }
    }

    /** A real burst - every frame close to every other one - still collapses. */
    @Test
    fun aBurstWhereEveryFrameResemblesEveryOtherStaysOneGroup() {
        val base = 0x0F0F_0F0F_0F0F_0F0FuL.toLong()
        val items = listOf(
            Item(1, base),
            Item(2, base xor 0b1L),
            Item(3, base xor 0b110L),
            Item(4, base xor 0b11000L),
        )

        val groups = groupSimilarItems(items, hashOf = { it.hash }, maxDistance = 5)

        assertEquals(1, groups.size)
        assertEquals(listOf(1, 2, 3, 4), groups.single().map { it.id })
    }

    @Test
    fun groupingIsDeterministicForTheSameInputOrder() {
        val items = (0 until 40).map { Item(it, chainedHash(it, bitsPerStep = 3)) }

        val first = groupSimilarItems(items, hashOf = { it.hash }, maxDistance = 5)
        val second = groupSimilarItems(items, hashOf = { it.hash }, maxDistance = 5)

        assertEquals(first.map { group -> group.map { it.id } }, second.map { group -> group.map { it.id } })
    }

    @Test
    fun itemsWithoutAHashOrWithAFeaturelessOneAreLeftOut() {
        val featureless = 0L
        val items = listOf(
            Item(1, 0x0F0F_0F0F_0F0F_0F0FuL.toLong()),
            Item(2, 0x0F0F_0F0F_0F0F_0F0FuL.toLong()),
            Item(3, featureless),
            Item(4, featureless),
            Item(5, null),
        )

        val grouped = groupSimilarItems(items, hashOf = { it.hash }, maxDistance = 5)
            .flatten()
            .map { it.id }

        assertEquals(listOf(1, 2), grouped)
    }

    @Test
    fun cancellationIsCheckedWhileScanning() {
        val items = (0 until 20).map { Item(it, chainedHash(it, bitsPerStep = 8)) }
        var checks = 0

        groupSimilarItems(items, hashOf = { it.hash }, maxDistance = 5, checkActive = { checks++ })

        assertTrue("the outer scan has to be interruptible", checks >= items.size)
    }

    /**
     * A hash [step] steps along a chain, flipping [bitsPerStep] fresh bits each step.
     * Kept balanced enough in popcount that `isFeatureless` does not filter it out.
     */
    private fun chainedHash(step: Int, bitsPerStep: Int): Long {
        var hash = 0x0F0F_0F0F_0F0F_0F0FuL.toLong()
        repeat(step * bitsPerStep) { bit -> hash = hash xor (1L shl (bit % 64)) }
        assertNotEquals(0L, hash)
        return hash
    }

    private data class Item(val id: Int, val hash: Long?)
}