# ChangeDistiller

This repository contains an updated version of [ChangeDistiller](https://bitbucket.org/sealuzh/tools-changedistiller/wiki/Home) (from Beat Fluri, et al.), which AutoVCS uses to extract and summarise changes to Java source code files.

Our improvements are:
- Support for Java 8 - 17
- An improved recursive differencing algorithm, which extracts changes made inside of individual classes (or class-like structures, such as interfaces and records) and methods rather than simply stopping at the new class or method and ignoring changes inside of it.

