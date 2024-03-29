# Description

This is a system to track basketball games and provide useful statistics about players, games and teams. It is very much still in development.

Here is a prototype of the game logger UI: ![A gif of a game being tracked](demo.gif)

And here is the analysis page shot chart: ![A shot chart colored by points per shot](shot-chart.png)

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

Start the server:
```sh
clj -M -m bball.core
```

To enable tailwindcss:
```sh
npx tailwindcss -i input.css -o ./resources/public/css/compiled/output.css --watch
```

## Deploy

Create the css file:
```sh
npx tailwindcss -i input.css -o ./resources/public/css/compiled/output.css --minify
```

Install npm modules:
```sh
npm install
```

Compile the frontend javascript files:
```sh
npx shadow-cljs release app
```

Build the uberjar:
```sh
clj -T:build uber
```

Run the uberjar
```sh
java -jar target/standalone.jar
```

## Docker

### Compose

Runs a transactor and peer server
```sh
docker compose up -d
```

### Stack

Start the local image registry service:
```sh
docker service create --name registry --publish published=5000,target=5000 registry:2
```

Build the stack images:
```sh
docker compose -f docker-stack.yml build
```

Push the stack images to the local image registry:
```sh
docker compose -f docker-stack.yml push
```

Deploy the stack:
```sh
docker stack deploy --compose-file docker-stack.yml clj-bball
```