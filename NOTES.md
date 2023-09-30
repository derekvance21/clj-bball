# Notes

## Todo

- [ ] send all the games as just a single vector of maps, so the entire thing can just be transacted
- [ ] select color scheme
- [ ] select zone layout
- [ ] exclude button in the team selector. Doing select all then unselect given teams isn't great
- [ ] bug where, if you have in your db teams already with :team/name and you try to create new game, you can't set the team name from home/away to an already existing :team/name
    - this is related to transacting the team `on-change` with a re-frame event-db, rather than `on-blur` with a reagent component (I think?). At least that'd be a starting point.
- [ ] free throw lane violation? Rn, counts as a 'miss', but really it's something else
    > According to NCAA, free throw doesn't count, and no attempt is given to the shooter. The player who is charged with the lane violation is also charged with a turnover. This is also true if it is the shooter who commits the lane violation by stepping on/over the free-throw line. No free throw attempt, and a turnover to the shooter. Just to go deeper, if it is a lane violation on both teams, there is no free throw attempt, and possession is dtermined by the possession arrow. If the shooting team retains possession, no turnover is charged to anyone. If the non-shooting team gets the ball, there is a change in possession, and a turnover is charged to the shooting-team player who committed the violation.
    [source](http://www.sportsjournalists.com/forum/threads/hoops-scoring-question.51438/)
    But it's not that interesting to me to charge a turnover for that. Although I would like to be able to not count it as a missed free throw for the shooter (maybe unless they commit the free throw violation)
- [ ] so ideally, you'd be able to load a game into the editor, and then be able to add new things
    - so maybe later if I want to add a contested thing to the game, I could load up that game and then go through each action, not have to change anything except the contested-ness. Would save a ton of time
- [ ] POST endpoint to transact new game from frontend
- [ ] use managed database for docker-stack.yml transactor
- [ ] delete player off of bench
- [ ] use interceptors for validation - events should be very simple, and use interceptors like enrich for validation and stuff. Like subbing someone out dissoc'ing the player if they were a shoot/rebound/stealer. That should be done via an interceptor, not cond-> logic in the event.
    - [ ] RELATED: datoms with empty :ft/results are being logged. Lots of [_ :ft/results [] _]. It happens every time you set a shot. Also, when you set a shot, :ft/attempted 0 is also being set (unnecessarily)
- [ ] I'm now using the preview db to basically transact on any action input change. So maybe I should just drop the :action part of app-db and just transact to conn every time? I guess I was originally worried about performance, but this doesn't seem to be an issue. This would also simplify adding certain action attributes like offense/players and defense/players, b/c I could just directly transact them when an action starts. And, it also allows me to do the fouled shot with a certain lineup, press sub and make substitutions after the 1st free throw, then set the missed free throw rebounder as someone who just subbed in
- [ ] generated court svg with specified dimensions
- [ ] add clojure/spec to app-db
- [ ] lineup stats
- [ ] individual stats
- [ ] use :pre and :post assertions in important functions
- [ ] customize visible stats
- [ ] support mobile
- [ ] edit already transacted action. But you should think of it more like editting a possession, b/c you don't really want to throw off the possession alternating pattern, although this is broken at periods, so... So if you allowed just randomly deleting one possession, then you'd need a way to insert a possession in between others. Also, something you might need is editting the period of past possessions.
- [ ] So like, maybe there's a way to show the end of a period in the render-possessions component. And it would insert some kind of possession end marker in between the :game/possession 's, so that you could see end of periods. It'd need to be a stateful-transducer-kind-of-thing, but I don't think it'd be too hard

### Completed

- [X] save game instead of upload to server (for now)
- [X] see if I can run the transactor correctly with a 1GB memory droplet. It works. 512MB memory droplet gets an error from the JVM
- [X] bug: if there's a free throw and people are coming into the game that haven't yet, you have to add them. But then they aren't in on-bench-ft, so they aren't available to select, so you have to deselect your free throw and then make it a free throw action again, and they'll be there
- [X] customize team/name
- [X] have a button to activate substitutions, which would also allow deleting players. This gives more space for other things there, like a second column for "object" players
- [X] two column players inputs
- [X] when a steal is selected, if the offense has a player of the same number, it'll highlight the offensive player too, rather than just the defensive player. I had this bug earlier with action/types, I think
- [X] sending datomic db to datascript
- [X] converting datomic db to datascript db
- [X] disable "Add" button if action is invalid, form validation reporting
- [X] Fix bug where you can add empty action by only selecting player. Would be nice to use a proper reagent form, using custom validators like the ol' Angular ReactiveForm has
- [X] use shared logic for these clickable button things
- [X] fix bug where on free throws with at least one miss and make, setting a rebound is required even though the make could've been at the end
- [X] have bonus do correct number of free throws - maybe a manual button to add free throw attempts? (different rules in different leagues)
- [X] fix bug where, at start of game, clicking on court causes crash
- [X] button-ify UI elements. Nobody likes using a select dropdown (however convenient it is for me, as a dev). What would be nicer is to have the selectable players all visible, and you click on their tile to select them.
    - [X] First thing is to button-ify the action/types. The radio selector is kind of lame
    Idea: you can't select a shot or action type until a player is selected. After it's selected, you can choose the action type (turnover, bonus, technical, or slicking a shot on the shot svg). After that happens, different players can be selected. If it's a miss (and reboundable?), you can select any player or team, on either team. If it's a turnover, you can select players from the defense to set a stealer. For a shot, there'll be a button foul? where you can set free throw attempts. If it's selected, there'll be vertically connected make/miss button pairs, for however many free throws there were. So one of each is required, and, if the last free throw is a miss, it'll prompt for a rebound player or team. For a bonus, maybe you can select single bonus or double bonus, and that'll determine how many free throws there are. Same thing about ending with a miss prompting for a rebound. For a technical, it'll be two free throws, no prompt for a rebound
- [X] buttons for team, to select init/team, but also to optionally set rebound/team?
- [X] the players map should have players in a set, to not allow duplicates
- [X] Button-ify the player selectors
- [X] have an action in progress above the most recent possession so that inputter can verify action before adding
- [X] start new quarter should be a checkbox, just in case you click it on accident and want to revert that
- [X] Start new game
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
- [X] buzzer beater shots won't have a rebounder, but rn it requires one to submit the action...
- [X] get initial frontend database DB from server /db endpoint
- [X] shot chart! Input: list of [:shot/angle :shot/distance] tuples. Output: icons on the court for every shot 
- [X] maybe disable Add button and other action buttons while sub? - I made a mistake this way once
- [X] here was a scenario - shooting foul, then technical. So you want to log the fouled shot and free throws. But then after you do so, you have to get the possession back to the same team to shoot the technicals. So which goes first. Also does the tech'ee get a turnover?
    - ANSWER: definitely log the turnover to the player (or none, for team/coach) that received the tech


## Docker Issue Log

- when I ran the datomic-dev-transactor image as a container in a droplet with another container running the a clojure repl with the peer library loaded, htop showed roughly 860 MB of memory usage
- when I was using 1GB memory droplets, once I would `(require '[datomic.api :as d])`, things would start to hang and eventually crash

- here's the deal:
- if I run the transactor with config `host=0.0.0.0` and `-p 4334:4334 -p 4335:4335`, I can `nc <private_ip> 4334` there from another droplet and connection opened.
- however, when, from that other droplet, I create a datomic-pro docker container with `--network=host`, I can do `nc <private_ip> 4334` and get connection opened, but if I `bin/repl` and try to create-database, I get that ActiveMQ connection refused error

- so, I tried to run the transactor with config `host=<private_ip>` and `-p 4334:4334 -p 4335:4335`
- when I did that, I couldn't `nc <private_ip> 4334` from even the host droplet (with ufw allowing those ports). I'd get connection refused (indicating that a port wasn't open on that host).
- but I could still do `nc 0.0.0.0 4334` and `nc localhost 4334` and get connection succeeded
- but also, i get a lot of the ActiveMQNotConnectedException on the *transactor* startup, even

- from inside the docker container where the transactor is running, I can reach the other droplets just fine using their public and private IP's

- now:
- when I run the transactor with config `host=<private_ip>` and `--network=host`, I can create a datomic-pro docker container with `--network=host` on a different droplet and get `create-database` to work inside `bin/repl`

- the other experiment I want to do is run it as a service rather than an individual container. The idea being that the docker swarm stuff will include a viable network
- when I run the service with the image that has `host=0.0.0.0` without publishing ports, I can enter the container and `nc -v localhost 4334` just fine, can't do `nc -v <private_ip>` from the container, and can't do `nc -v <private_ip>` from the host machine
- when I run the service with the image that has `host=<private_ip>` without publishing ports, I get ActiveMQNotConnectedException

- now I just ran some experiments starting the transactor service with --network name=my-network,alias=transactor and the transactor starting with a config `host=transactor`. But then when I try to connect to it from another container connected to the same network, I get connection refused with `nc -v transactor 4334`. I even get connection refused when inside the container with `nc -v 0.0.0.0 4334`

- here was another experiment where I ran the transactor service with `host=<private_ip>` and `--network host`. Then I go to another droplet and run datomic-pro container with --network host. And when I bin/repl and try to create-database, I get :db.error/read-transactor-location-failed Could not read transactor location from storage.
