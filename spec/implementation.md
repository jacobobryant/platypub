Use a `:platypub.fx/swap!` biff.fx handler for swapping on atoms, for example:

```
[:platypub.fx/swap! :platypub/my-atom `update :foo `inc]
```

The first keyword is used to get an atom from the system map. qualified symbols
in the vector are resolved to functions. Then unit tests for the biff.fx machine
state can still show what the function does as data.

In development, emails that would be sent in production should be printed to the
console. Their text content is printed, not their html content. This includes
signin emails.

Use https://github.com/igrishaev/remus for parsing feeds.

No two sends for the same publication may share a post ID. Sends must always be
created in a transaction that enforces this invariant.
