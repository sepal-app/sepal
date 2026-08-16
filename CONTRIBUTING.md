# Contributing to Sepal

Thanks for considering a contribution. Sepal is a botanical collection
management system, and it gets better when the people who actually run
collections help shape it.

## The contributor license agreement, and why it exists

Before your first pull request can be merged you need to sign the
[Contributor License Agreement](CLA.md). Signing takes one comment on your pull
request — see [How to sign](CLA.md#how-to-sign).

Rather than leave you to guess at the motive, here it is plainly:

- **Sepal is licensed under the [AGPL-3.0](LICENSE), and that is not changing.**
  Every release is free software. You can run it, read it, modify it, and host
  it yourself, and nothing in the CLA takes that away or lets it be taken away
  retroactively.
- **A hosted version of Sepal funds its development.** That is the business, and
  the AGPL protects it: anyone who hosts a modified Sepal as a service has to
  publish their modifications.
- **The CLA exists so that Sepal LLC can also sell commercial licenses.** Some
  organizations — universities and government-funded institutions especially,
  which is much of Sepal's audience — have procurement rules that refuse
  AGPL-licensed software outright. Being able to offer those organizations a
  commercial license turns a lost user into a customer. That requires the right
  to license the whole codebase under terms other than the AGPL, which in turn
  requires that right for every contributed line.
- **You keep your copyright.** The CLA is a license grant, not an assignment.
  Your contributions remain yours to use however you like, including in other
  projects.

If that trade is not one you want to make, that is a completely reasonable
position. Bug reports, reproductions, feature discussion, and documentation
feedback in issues are valuable and require no agreement at all.

## Development setup

See [README.md](README.md) for prerequisites, the WFO Plantlist database, and
environment variables.

Dependencies come from [devenv](https://devenv.sh), entered automatically by
[direnv](https://direnv.net):

```bash
direnv allow          # once, per checkout
devenv shell          # or just cd into the directory, with direnv active
```

The architecture — this is a Polylith monorepo, with `bases/`, `components/`
and their interface conventions — is documented in [AGENTS.md](AGENTS.md). It is
written for coding agents, but it is the most complete description of how the
code is organized and is worth reading before a non-trivial change.

## Before you open a pull request

```bash
bin/lint                                                  # clj-kondo and cljfmt
clojure -M:cljfmt fix                                     # fix formatting
clojure -M:dev:test:test-runner --focus :unit             # unit tests
clojure -M:dev:test:test-e2e:test-runner --focus :e2e     # end-to-end tests
```

CI runs the lint and both test suites on every pull request, so running them
locally first saves a round trip.

A few expectations:

- **Include a test.** New behavior needs a test that fails without the change.
  Bug fixes need a test that reproduces the bug.
- **Keep the change focused.** Unrelated refactoring and reformatting in the
  same pull request makes review much harder. Open a separate one.
- **Import from interface namespaces**, never from `core` directly. See
  [AGENTS.md](AGENTS.md) for this and the other Polylith conventions.
- **Discuss large changes first.** Open an issue before writing a lot of code,
  so we can agree on the approach before you invest in it.

## Reporting bugs and requesting features

Open an issue. For bugs, the useful things to include are what you did, what
happened, what you expected instead, and — if you can — the smallest set of
steps that reproduces it.

## Security

Do not report security vulnerabilities in a public issue. Email
brett@sepal.app instead.
