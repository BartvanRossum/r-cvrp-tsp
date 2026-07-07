# r-cvrp-tsp

A repository containing code and instances accompanying the paper:

> B. van Rossum, R. Chen, and A. Lodi (2026). *Enforcing TSP-Optimality in Fair Vehicle Routing by Cutting Planes*.
> [[arXiv:2604.23748]](https://arxiv.org/abs/2604.23748)  

## Overview

This repository provides an exact branch-price-and-cut algorithm for the range capacitated vehicle routing problem with TSP-optimality (R-CVRP-TSP). The method enforces TSP-optimality through TSP-optimality cuts, which forbid TSP-dominated arc sequences. It also includes the postprocessing heuristic of van Rossum et al. (2025), used as a benchmark in our computational experiments.

## Running the experiments

The computational experiments are run through [MainTSPCluster.java](src/vehicleRouting/scripts/MainTSPCluster.java), which is intended to be run on a compute cluster as an array job. It takes a single integer command-line argument that indexes into the full grid of experimental settings (number of customers, alpha, instance replication, and cut configuration).

To run a single job locally (using CPLEX on the classpath, see `.classpath`), compile the project and run, e.g.:

```bash
java vehicleRouting.scripts.MainTSPCluster 0
```

Argument `0` corresponds to the first setting combination: 15 customers, `alpha = 1.01`, instance replication `t = 0`, and no TSP-optimality cuts. It reads the instance from `dataCVRP/instances/n15_k5_0.txt` and the reference solution from `dataCVRP/solutions/solution_n15_k5_0.txt`, then writes the optimized solution to `solution_n_15_alpha_1.01_t_0_cuts_false_lifting_false.txt` and a run log to `logger_n_15_alpha_1.01_t_0_cuts_false_lifting_false.csv` in the working directory.

Other argument values enumerate the remaining combinations (customer counts `{15, 20, 25, 30}`, alphas `{1.01, 1.05, 1.10}`, 20 instance replications, and 3 cut configurations — no cuts, cuts, or cuts with lifting), for a total of 720 jobs.

## Citation

If you use this code or these instances in your research, please cite:

```bibtex
@article{vanrossum2026tspoptimality,
  author  = {van Rossum, B.T.C. and Chen, R. and Lodi, A.},
  title   = {Enforcing TSP-Optimality in Fair Vehicle Routing by Cutting Planes},
  note    = {Preprint: \url{https://arxiv.org/abs/2604.23748}},
  year    = {2026},
}
```

## License

The code and instances are released under the [MIT License](LICENSE).
