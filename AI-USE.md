# AI Use Disclosure

I used AI tools, primarily ChatGPT as learning and documentation aids while completing M0b. I used Claude at the end to add commments. 

## ChatGPT Browser

I used ChatGPT to help me understand concepts and interpret compiler/test output rather than to generate the completed implementation. Specifically, I used it to:

- Understand Java records, record components, compact constructors, and identifiers.
- Understand how `Position` represents chess coordinates using 0-based file and rank values.
- Understand the purpose and expected behavior of `offsetOrNull`, including why an off-board offset returns `null` while directly constructing an invalid `Position` should throw an `IllegalArgumentException`.
- Understand how characters and their numeric values can be used to translate algebraic chess notation such as `"e2"` into 0-based coordinates.
- Understand how `this` works inside the `Color` enum and what methods such as `pawnDirection()` and `pawnStartRank()` represent.
- Interpret Maven compilation errors and JUnit test failures while debugging my implementation.
- Ask general Java syntax questions, such as iterating through strings, using `charAt()`, splitting strings, and working with character values.
- Used to generate this doc based on the entire chat I used for this milestone

I wrote and debugged the M0b method implementations myself based on my understanding of these concepts and the provided tests.

## Claude Code

After my implementation was working, I used Claude to add/improve comments in both Position.java and Color.java

I used Claude Code because it was already an integrated plugin on my IntelliJ and I didn't feel like setting up codex just to add comments

## Verification

I ran the provided Maven test suite using:

`./mvnw test`

and reached `BUILD SUCCESS`.