package me.victor.words

import edu.mit.jwi.Dictionary
import edu.mit.jwi.IDictionary
import edu.mit.jwi.item.IIndexWord
import edu.mit.jwi.item.POS
import me.victor.words.Hand.LEFT
import me.victor.words.Hand.RIGHT
import java.lang.ClassLoader.getSystemClassLoader

/**
 * Simple command line program to find all words in the WordNet dictionary that
 * match a certain part of speech (POS), begin with a certain prefix, and
 * which are fairly close to being evenly typed by two hands.
 *
 * WordNet dictionaries are in `src/main/resources/dict/` and I got them from
 * here: [https://wordnet.princeton.edu/wordnet/].
 *
 * @author Victor Schappert
 * @since 20171210
 */
fun main(args: Array<String>) {
    val poses = argsForName("pos", args).map(POS::valueOf)
    val prefixes = argsForName("prefix", args)
    filterWords(poses, prefixes)
            .sorted()
            .distinct()
            .forEach(::println)
}

private fun argsForName(name: String, args: Array<String>): List<String> {
    val start = args.indexOfFirst { it == "-" + name }
    var end: Int = start
    if (0 <= start) {
        while (end + 1 <= args.lastIndex && !args[end + 1].startsWith("-")) ++end
    }
    return args.slice((start + 1)..end)
}

private fun filterWords(poses: List<POS>, prefixes: List<String>) : List<String> {
    val dictionary = dict()
    if (! dictionary.open()) {
        throw IllegalStateException("Can't open dictionary")
    }
    try {
        val prefixMap = prefixes.groupBy(String::length).toSortedMap()
        return poses.map { dictionary.getIndexWordIterator(it) }
                .map(Iterator<IIndexWord>::asSequence)
                .flatMap(Sequence<IIndexWord>::toList)
                .map { it.lemma }
                .filter { 3 < it.length && it.indexOfAny(charsToFilter) < 0 }
                .filter(String::isMostlyAlternateHandTyped)
                .filter { prefixMap.isEmpty() || it.hasAnyPrefix(prefixMap) }
    } finally {
        dictionary.close()
    }
}

private val charsToFilter = charArrayOf('_', '\'')

enum class Hand { LEFT, RIGHT }

private val leftHandChars = charArrayOf('q', 'w', 'e', 'r', 't',
                                        'a', 's', 'd', 'f', 'g',
                                        'z', 'x', 'c', 'v', 'b')

private val charMap = charMap()

private fun charMap() : Array<Hand?> {
    val charMap = Array<Hand?>(128) { null }
    leftHandChars.forEach {
        charMap[it.toInt()] = LEFT
        charMap[it.toUpperCase().toInt()] = LEFT
    }
    ('a'..'z').forEach {
        if (charMap[it.toInt()] == null) {
            charMap[it.toInt()] = RIGHT
            charMap[it.toUpperCase().toInt()] = RIGHT
        }
    }
    return charMap
}

private fun String.isAlternateHandTyped() : Boolean {
    var prev: Hand? = null
    forEach {
        val hand = charMap[it.toInt() and 0x7f]
        if (hand == prev) {
            return false
        } else {
            prev = hand
        }
    }
    return true
}

private const val SAME_HAND_RATIO = 0.20

private fun String.isMostlyAlternateHandTyped() : Boolean {
    // Rule 1: No adjacent repeated characters, e.g. 'tt'.
    // Rule 2: Not more than X% of transitions may be to same hand.
    var prevHand: Hand? = null
    var prevChar = '\u0000'
    var sameHandCount = 0
    forEach {
        val hand = charMap[it.toInt() and 0x7f]
        if (hand == prevHand) {
            if (it == prevChar) {
                return false // Repeat character.
            } else {
                ++sameHandCount
            }
        }
        prevHand = hand
        prevChar = it
    }
    return sameHandCount.toDouble() / (length.toDouble() - 1.0) < SAME_HAND_RATIO
}

private fun String.hasAnyPrefix(prefixMap: Map<Int, List<String>>) : Boolean {
    prefixMap.entries.forEach {
        if (it.key <= length ) {
            if (it.value.any { prefix -> startsWith(prefix, ignoreCase = true) }) {
                return true
            }
        }
    }
    return false
}

private fun dict() : IDictionary {
    val url = classLoader().getResource("dict/")
    return Dictionary(url)
}

private fun classLoader() = getSystemClassLoader()