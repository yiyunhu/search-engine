# search-engine

This software illustrates the architecture for the portion of a search
engine that evaluates queries.  Documents are stored in Lucene 8.1.1
indexes. It can support the following algorithms:

• Two exact-match retrieval algorithms: RankedBoolean and UnrankedBoolean

• Two best-match retrieval algorithms: BM25 and Indri

• Pseudo relevance feedback

• Features and test learning to rank (LTR)

• Diversified ranking algorithms

All java files are in QryEval folder. Meanwhile, QryEval.java is the main class.
