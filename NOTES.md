- I think I don't want to do steals
- I like the <team> sub <in|out> <number>+
- commas are whitespace makes sense
- assists are too subjective
- could do something in parsing where you have a state of the current team and player, and if you don't modify that with a team or number designation, then you can string together actions, so like:
C 3 3 miss 2 reb 33 rim miss reb rim make
above means C 3 missed a 3, C 2 got the rebound, C 33 missed at the rim, then C 33 got the rebound, then C 33 made a rim shot
and then it's optional to do this shorthand, and you could just explicitly write it
and also: if you just say <team>, that means it's a team action. So C 3 miss V reb works just fine
- I guess what all this is saying is that you shouldn't have newline dependent parser.
So you could have
C 3 miss V reb
10 3 make
C turnover
V 5 rim miss reb 10 3 make
And this would be valid syntax, even if on just one line
And thus everything *could* be done in one line, with just spaces. Then the parser has to do the job of parsing out possession changes, rather than the new line doing that
- on free throws, I like just doing explicit (make|miss){1,3}
- should I do
3pt := "3"
or 3pt := "three"
I think "three" makes sense, to keep with lowercase naming for actions, and numbers reserved for players and periods
- would be possible to do a lot of validation, and some extra lines would help
for example, could be a warning if you list a player doing something when they're not in the game
could list scores whenever you want, and it'll check it. But also, there should just be a running score up as you input
should have list of players currently in the game
could be warning if a missed shot doesn't have a rebound attached
- **everything is data**
this is an idea that could use some work here, but generally it works kinda
should be easy to attach new kinds of data to a game, like fouls or timeouts, etc.
- something to watch for: for a team offensive reboud, have to do, for ex.:
V 22 rim miss V reb
To clarify that it's not a V 22 rebound, but a V rebound
- so it's kinda like:
parser turns string into list of strings, intermediate step transforms each string into a command
interpreter interprets commands and produces game
- if you wanted (and it would only be by convention), each line would be a possession. So if the previous ended with a miss, the next would start with a rebound. And then you'd only have to list the team once per line (except for offensive team rebound)
The complication for this (and it really isn't a problem, just that the convention is broken a bit) is if you wanna record blocks (which don't change possession on their own) and charting player defensive fouls
- So everything not enclosed in anything is a game action. So it's reserved for what happens when the clock is running and game is being played
  - **THIS IS MY OLD THINKING. SEE FARTHER BELOW AT "BRACKET MACRO" FOR HOW I RESOLVE THIS**
  - but you need a way to modify the *context* of the game, i.e. substitutions, period, etc.
  - so this is where you use (either brackets or parentheses) to modify the *environment* (players in the game and available for actions)
  - so like [C in 3 14 33 out 2 30 22 V in 10 out 41] OR could be: [C 3 14 33 in 2 30 22 out V 10 in 41 out] to align with <selector> <action> motif
  - so the opening bracket is like: we're going into a different interpreter "mode" to modify the context. "C" means select the C team, "in" means we're adding whatever numbers come next to the game. Then each number encountered next is added to the game for the C team. Then "out" comes, and since we are already selecting the C team, the numbers that come next will be removed from the C team. Then V is encountered, so we switch to team V, and similar happens for "in" and "out" and those players
  - so with these semantics, could do something like this: 
  - [in C 2 3 14 22 33 V 0 5 10 12 22], which would set the starters at the beginning of the game, and the following would be equivalent: [C in 2 3 14 22 33 V 0 5 10 12 22], because you select the "in" mode and then input players, but when you switch to team "V" mode, you're still in "in" mode and can input numbers from there
  - the way I'm making this is very much a stateful language, but that's ok I suppose (as far as I can tell), but may pose difficulties for validation later on, if I had to guess
  - And now you have a way to change the period: [period 2]. And the sematics could dictate that you start a new possession whenever a period modifier is encountered

## Grammar [DEPRECATED]

<file>            ::= <statement> | <statement> <whitespace> <file>
<statement>       ::= <team> | <number> | <multi_number> | <action> | <comment>
<comment>         ::= "[" .* "]" ; should be a lazy quantifier here, or use [^\]]*
<multi_number>    ::= "(" <number_list> ")"
<number_list>     ::= <number> | <number> <whitespace> <number_list>
<number>          ::= \d | \d \d
<team>            ::= <word>
<action>          ::= <word>
<word>            ::= \a | \a <word>
<whitespace>      ::= <whitespace_token> | <whitespace_token> <whitespace>
<whitespace_token>::= \s | ","
; \a is alphabetic character [a-zA-Z]
; \s is whitespace character
; \d is digit character [0-9]
; . is wildcard character
; you differentiate between <action> and <team> because at the start you have a command to set the teams, and then the parser tries to match the <team> **before** the <action>. could also do it where <team> is always uppercase alphabetic, and <action> is always lowercase alphabetic.

- so now, a .game file is list of statements, and you can reduce or scan the list of statements to get the end state or intermediate states of the parser, which would have its own state (what selectors are selected, is it in command mode, etc.), the context (what players are in the game, what period it is, etc.) and also the game state (the score, plays taken, etc.). So it's pretty cool that I've invented a language for this. It's not turing-complete tho lol
  - and then the output you want for a "game" is a list of possessions, where each possession is a list of plays, where each play has two 5-arrays of each team's players and the action that occurred in that play
  - if the priority end goal is the four factors dashboard, you need to be able to 
  - and depending on the reducing or scanning function, you can get different information. Like one could be the final score and the box score stats of all the players. And another could be the four factors stats for each team. Or you could combine them with an abstraction somehow. Like I want (combine four-factors-fn box-score-fn) or something

- what do you do when the players in for a possession is split in half? Like team offensive rebound, substitution, score. Does the subbed out player get credit for the score in their offensive rating? Does the subbed in player get credit for the offensive rebound? Questions here...
  - this is quite a small problem, as this won't happen all that often
  - both count as their possession - add extraneous information. Imagine a line change, all 10 (possibly 20 if both teams) players get credit 
  - neither count as their possession - lose information
  - half a notion of half-possessions. Ups the complexity. Need to stitch together half possessions where needed, and observe them apart as well. Also, with a half possession - a player who got the offensive rebound is subbed out. Does this count as a non-scoring possession? Or maybe you ignore it for possession scoring stats, but include it in rebounding stats. Ahhhhhh
- btw, applying team selector **has** to reset number selector. Because `V 10 rim miss V reb` is supposed to be a V team rebound, not a V 10 rebound

### Bracket macro - I like parentheses better, actually. Reminds me of function invocation
- So the parentheses syntax is like a "macro". So `A (10 12 14) in` is (impurely) equivalent to `A 10 in 12 in 14 in`
  - so now the number selector can select multiple numbers. When this is the case, on the following action, it runs the action on each number. Genius.
  - But probably should be a check that the following action can only be a substitution. Also, could think about whether the end of <macro> <action> should result in number selector being blank, or number selector retaining last one in the vector. 
  - So with above example, should the number selector be set to 14, or to blank?
  - example:
    ```
    V (12 10) in (5 22) out
    C (22 3 14) out (2 20 33) in 2 rim make
    ```
  - above illustrates that can flip the order of in/out actions as desired, and that the team selector is still set after doing subbing actions. Not that you'd *want* to write it like this, but that's the semantics

### Notating period changes
- this is more than just fluff stuff, because it always marks the boundary of possessions
- since we already used parentheses (or brackets, if I change my mind back), this might be a good use of brackets?
- so, we really just need a way to mark a period change. So we could just have `period` be an action that increments the interpreter's internal period counting state, and then also marks a new possession

### Comments
- this is where brackets could be good. I've always like enclosing comments that don't just wait for a newline. And this is similar to how brackets are used in plays, say. They provide extra insight into the scene
