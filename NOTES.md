# Notes

## Todo

- [ ] Start new game
    - supporting custom teams, datetime, other info
- [ ] Fix bug where you can add empty action by only selecting player. Would be nice to use a proper reagent form, using custom validators like the ol' Angular ReactiveForm has
- [ ] Add clojure/spec to app-db
- [ ] Use interceptors for action validation
- [ ] lineup stats
- [ ] individual stats
- [ ] the players map should have players in a set, to not allow duplicates
- [ ] svg for the whole court, but then mask parts
- [ ] fix bug where on free throws with at least one miss and make, setting a rebound is required even though the make could've been at the end
- [ ] start new quarter should be a checkbox, just in case you click it on accident and want to revert that
- [ ] use :pre and :post assertions in important functions
- [ ] customize visible stats
- [ ] support mobile
- [ ] button-ify UI elements. Nobody likes using a select dropdown (however convenient it is for me, as a dev). What would be nicer is to have the selectable players all visible, and you click on their tile to select them

- [X] Save new db to local/session storage on add action
- [X] refactor app-db type query operations into db.cljs, and potentially move the datascript db logic into a new file. app.events and app.subs share a lot of the same logic, and app.db is what that's supposed to be for - logic related to the app-db structure
- [X] this would be a big change - make the datascript connection behave more like the app-db and :db co/effect, where the coeffect is @conn rather than conn. Then I guess the coeffect would be the datascript-db value, and it could effect with the value of the new datascript db, which would then use reset-conn! with the new db, similar to how the :db effect uses `(reset! app-db value)`. And I guess an intermediate effect could be called with tx-data, and then it'd put the result of db-with into the :datascript-db effect. So it wouldn't use `transact!` directly? But then, wouldn't you might-as-well put the datascript db into app-db, and just `(update db :datascript-db d/db-with tx-data)`?
- [X] Fix bug where, after setting a rebound on a missed ft, made free throws is set to = attempted free throws, and thus no rebound should be possible. Maybe using a rebound interceptor, or something
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
