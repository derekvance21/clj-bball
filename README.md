# Description

This is a system to track basketball games and provide useful statistics about players, games and teams.

# Goals

As a guiding philosophy, the statistics provided in this system should help people understand their own and other teams, for team scouting purposes. High granularity individual statistics are of less interest.

## Statistics

Here are the statistics that the system should be able to provide. Each statistic should be filterable to the granularity of player, team, and lineup, with the scale of game(s) and season(s):

- **Per Game**
    - pace (via game minute calculations, as pace is derived from possessions per minute)
    - points per game

- **Per Possession**
    - net rating (NRtg)
    - offensive rating (ORtg)
    - defensive rating (DRtg)
    - turnover percentage (TOV%)

- **Per Shot**
    - offensive rebounding percentage (ORB%)
    - defensive rebounding percentage (DRB%)
    - three point attempt rate (3PAr)
    - FG% by distance
    - % of FGA by distance
    - free throw rate (FT/FGA)
    - free throw attempt rate (FTA/FGA)
    - effective field goal percentage (eFG%)
    - true shooting percentage (TS%)

## Non-Goals

These are explicit non-goals of the system:

- assists
- date-based queries on games
- in-game timing information (minutes a certain lineup was in, etc.)

## Getting Started

To enable tailwindcss:
```sh
npx tailwindcss -i ./resources/input.css -o ./resources/public/css/compiled/output.css --watch
```

## Bugs

If there is a made shot with a missed free throw and a team defensive rebound, that might not be counted easily, because now you have to check if :ft/made is 0 where :ft/attempted is > 0. Also, for non-made shots with at least one missed free throw and at least one made free throw, who's to say whether it ended with a miss and was a team defensive rebound, or it ended with a make and no rebound either way.
