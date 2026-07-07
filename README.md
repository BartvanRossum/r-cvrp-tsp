# r-cvrp-tsp

A repository containing code and instances accompanying the paper:

> B. van Rossum, R. Chen, and A. Lodi (2026). *Enforcing TSP-Optimality in Fair Vehicle Routing by Cutting Planes*.
> [[arXiv:2604.23748]](https://arxiv.org/abs/2604.23748)  

## Overview

This repository provides an exact branch-price-and-cut algorithm for the range capacitated vehicle routing problem with TSP-optimality (R-CVRP-TSP). The method enforces TSP-optimality through TSP-optimality cuts, which forbid TSP-dominated arc sequences. It also includes the postprocessing heuristic of van Rossum et al. (2025), used as a benchmark in our computational experiments.

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
