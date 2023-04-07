# Notes

## Todo

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
- [ ] Save new db to local/session storage on add action
- [ ] Start new game
    - supporting custom teams, datetime, other info
- [ ] Implement undo action
    - pops the last action off the datascript db
    - if action is only one in possession, pops the possession, too
    - could result in period backtracking, which is good with the way I have it
- [ ] Use return maps in datascript queries
- [ ] Edit actions
    - use `(:_component-ref (entity db entid))` (check the datascript docs under `core/entity`)
- [ ] Add clojure/spec to app-db
- [ ] Support logging team rebounds
    - could just have a rebound/team? boolean schema
- [ ] lineup stats
- [ ] individual stats

