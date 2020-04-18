# CRUD-less TodoMVC

This is a full stack implementation of the TodoMVC application in Fulcro 3.0. It uses Hasura and Pathom to power the backend portion of the app.

This has been modified from the original [Fulcro 3.0 TodoMVC](https://github.com/fulcrologic/fulcro) to use a Hasura GraphQL server as the backend instead of a Fulcro server.

This application consists of three docker containers:
* nginx to serve static assets and act as a reverse proxy
* hasura, our GraphQL API server
* postgres, our database

## Setup

In order to run the application, you need to install:
* [Docker](https://www.docker.com/get-started) and [Docker Compose](https://docs.docker.com/compose/install/)
* [npm](https://www.npmjs.com/get-npm)
* [clojure](https://clojure.org/guides/getting_started)
* [hasura cli](https://docs.hasura.io/1.0/graphql/manual/hasura-cli/install-hasura-cli.html)

Once you have these installed, run
```bash
npm install
```
to install javascript dependencies.

We also need to migrate our database and configure hasura. We'll need hasura running to do that, and hasura is set up to automatically migrate the database when it starts. To start hasura, run
```bash
docker-compose up -d
```

To apply the correct hasura configuration, run
```bash
hasura metadata apply --project hasura
```

At this point, setup is complete. To shut down hasura, run
```bash
docker-compose down
```

## Running It

To compile the clojurescript, you can run either
```bash
npx shadow-cljs watch :main
```
to watch and recompile or
```bash
npx shadow-cljs compile :main
```
to compile once and exit.

Once your clojurescript has been compiled, you should make sure your docker containers are running with
```bash
docker-compose up -d
```

Then you should be able to access the todo app at http://localhost:3001.

## Developing

To tweak Hasura configuration and access GraphiQL, you'll need to start the hasura console. You can do this by running
```bash
hasura console --project hasura
```

(or just `hasura console` if you're in the `hasura` directory).

The console will then be available at http://localhost:9695. Any changes to the database will be recorded as migrations stored in the `hasura/migrations` directory. Changes you make to the Hasura configuration will be saved locally, but to check them in to source control you can run
```
hasura metadata export --project hasura
```
and observe the newly generated `hasura/migrations/metadata.yml` file.

Any changes to the Hasura schema will automatically become available as queries after a page refresh.

To understand the details of how this is put together, there is excellent documentation available for (among others):
* [Fulcro](http://book.fulcrologic.com)
* [Pathom](https://wilkerlucio.github.io/pathom/)
* [Hasura](https://docs.hasura.io/1.0/graphql/manual/index.html)
* [Shadow CLJS](https://shadow-cljs.github.io/docs/UsersGuide.html)
