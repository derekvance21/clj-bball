# Notes

## Todo

- [ ] Fix bug where, after setting a rebound on a missed ft, made free throws is set to = attempted free throws, and thus no rebound should be possible. Maybe using a rebound interceptor, or something
- [ ] Fix bug where you can add empty action by only selecting player. Would be nice to use a proper reagent form, using custom validators like the ol' Angular ReactiveForm has
- [ ] Save new db to local/session storage on add action
- [ ] Start new game
    - supporting custom teams, datetime, other info
- [ ] Add clojure/spec to app-db
- [ ] lineup stats
- [ ] individual stats
- [ ] the players map should have players in a set, to not allow duplicates
- [ ] svg for the whole court, but then mask parts

- [X] Refactor rebound from :shot/rebounder to :rebound/player and :rebound/off?
- [X] Support logging team rebounds
    - could just have a rebound/team? boolean
- [X] default play numbers in :players map
- [X] Refactor a lot of `::events/add-action` into `app.db`
- [X] Make `::subs/team` dependent on `::subs/datascript-db`
    - find the last possession in the last action and determine if possession change
    - re-frame/app-db should ideally only be for things outside the datascript db
- [X] Use `::subs/init-team` for the start of periods
    - jump-balls make it hard to know possession after end of period
    - will allow cutting a possession short at end of period
    - Ex: offensive rebound, then clock expires
        - lose possession - end of possession? YES
        - keep possession - end or continuation of possession? END OF POSSESSION
- [X] Implement undo action
    - pops the last action off the datascript db
    - if action is only one in possession, pops the possession, too
    - could result in period backtracking, which is good with the way I have it
