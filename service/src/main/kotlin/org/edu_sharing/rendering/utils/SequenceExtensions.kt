package org.edu_sharing.rendering.utils

/**
 * Returns a sequence that yields elements from the original sequence until the given predicate
 * evaluates to true, including the element that satisfies the condition.
 *
 * @param initState The initial state to be used in predicate and state transition functions.
 * @param predicate A function that takes the current state and an element, returning true if
 *                  the iteration should stop including the current element.
 * @param nextState A function that takes the current state and an element, returning the next state.
 * @return A sequence that includes elements from the original sequence until the predicate evaluates to true.
 */
fun <T, S> Sequence<T>.takeUntil(initState: S, predicate: (state: S, element: T)->Boolean, nextState: (currentState: S, element: T) -> S): Sequence<T> = sequence {
    var state: S = initState
    for (element in this@takeUntil) {
        if (predicate.invoke(state, element)) break
        yield(element)
        state = nextState.invoke(state,element)
    }
}
