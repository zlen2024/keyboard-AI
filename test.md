Here are the solutions formatted in English, using Mermaid diagrams for graphs, trees, and K-maps.
## **QUESTION 1**
### **a) Graphs**
#### **K_6 (Complete Graph with 6 Vertices)**
```mermaid
graph TD
    v1 --- v2
    v1 --- v3
    v1 --- v4
    v1 --- v5
    v1 --- v6
    v2 --- v3
    v2 --- v4
    v2 --- v5
    v2 --- v6
    v3 --- v4
    v3 --- v5
    v3 --- v6
    v4 --- v5
    v4 --- v6
    v5 --- v6

```
#### **C_6 (Cycle Graph with 6 Vertices)**
```mermaid
graph TD
    v1 --- v2
    v2 --- v3
    v3 --- v4
    v4 --- v5
    v5 --- v6
    v6 --- v1

```
#### **W_6 (Wheel Graph - Central Vertex connected to C_5)**
```mermaid
graph TD
    vc --- v1
    vc --- v2
    vc --- v3
    vc --- v4
    vc --- v5
    v1 --- v2
    v2 --- v3
    v3 --- v4
    v4 --- v5
    v5 --- v1

```
#### **Q_3 (3-Cube Graph)**
```mermaid
graph TD
    subgraph Inner Square
        000 --- 001
        001 --- 011
        011 --- 010
        010 --- 000
    end
    subgraph Outer Square
        100 --- 101
        101 --- 111
        111 --- 110
        110 --- 100
    end
    000 --- 100
    001 --- 101
    011 --- 111
    010 --- 110

```
### **b) Adjacency Matrix for K_4**
### **c) Isomorphism of Graphs G and H**
**Yes, graphs G and H are isomorphic.**
**Bijective Vertex Mapping f: V(G) \to V(H):**
 *  *  *  *  *  *  *  * Both graphs have 8 vertices, 10 edges, identical degree sequences (\text{deg}(v) \in \{2, 3\}), and adjacency structural properties are fully preserved under this mapping.
### **d) Compound Proposition Tree & Notations**
#### **Ordered Rooted Tree**
```mermaid
graph TD
    R["↔"]
    L1["¬"]
    R1["∨"]
    L2["∧"]
    R2_1["¬"]
    R2_2["¬"]
    P1["p"]
    Q1["q"]
    P2["p"]
    Q2["q"]

    R --> L1
    R --> R1
    L1 --> L2
    L2 --> P1
    L2 --> Q1
    R1 --> R2_1
    R1 --> R2_2
    R2_1 --> P2
    R2_2 --> Q2

```
#### **Notations**
 * **Prefix:** \leftrightarrow \neg \wedge p \, q \vee \neg p \neg q
 * **Postfix:** p \, q \wedge \neg p \neg q \neg \vee \leftrightarrow
 * **Infix:** \neg(p \wedge q) \leftrightarrow (\neg p \vee \neg q)
### **e) Full m-ary Tree Properties**
#### **i) Leaves in a full 3-ary tree with 150 vertices**
 * Using l = \frac{(m - 1)n + 1}{m}:
   
   
   *(Note: A valid full 3-ary tree requires n \equiv 1 \pmod 3, i.e., n=151 \implies l = \mathbf{101})*.
#### **ii) Vertices in a full 4-ary tree with 200 internal vertices**
 * Using n = m \cdot i + 1:
   
#### **iii) Internal vertices in a full 5-ary tree with 100 leaves**
 * Using i = \frac{l - 1}{m - 1}:
   
## **QUESTION 2**
### **a) Complete 5-ary Tree of Height 3**
```mermaid
graph TD
    R((Root))
    
    subgraph Level 1 - 5 Nodes
        R --- A1((A1))
        R --- A2((A2))
        R --- A3((A3))
        R --- A4((A4))
        R --- A5((A5))
    end

    subgraph Level 2 - 25 Nodes
        A1 --- B1((...))
        A1 --- B2((...))
        A1 --- B3((...))
        A1 --- B4((...))
        A1 --- B5((...))
    end

    subgraph Level 3 - 125 Leaves
        B1 --- C1[...]
        B1 --- C2[...]
        B1 --- C3[...]
        B1 --- C4[...]
        B1 --- C5[...]
    end

```
### **b) Expression Tree & Notations for (x+y)+((xy+x)/y)**
#### **Binary Tree**
```mermaid
graph TD
    N1["+"]
    N2["+"]
    N3["/"]
    N4["x"]
    N5["y"]
    N6["+"]
    N7["y"]
    N8["*"]
    N9["x"]
    N10["x"]
    N11["y"]

    N1 --> N2
    N1 --> N3
    N2 --> N4
    N2 --> N5
    N3 --> N6
    N3 --> N7
    N6 --> N8
    N6 --> N9
    N8 --> N10
    N8 --> N11

```
#### **Notations**
 * **Prefix:** + + x \, y / + * x \, y \, x \, y
 * **Postfix:** x \, y + x \, y * x + y / +
### **c) Evaluate Prefix Expression: + - 1 \, 3 \, 2 \, 1 \, 2 \, 3 / 6 - 4 \, 2**
 1. Evaluate rightmost sub-expressions:
   *    *  2. Expression simplifies to: + - 1 \,\, 3 \,\, 2 \,\, 1 \,\, 2 \,\, 3 \,\, 3
 3. Evaluate left operators:
   *  4. Final sum of remaining terms:
   
### **d) Truth Table for F(x,y,z) = x\overline{y} + \overline{z}**
| x | y | z | \overline{y} | \overline{z} | x\overline{y} | F(x,y,z) |
|---|---|---|---|---|---|---|
| 0 | 0 | 0 | 1 | 1 | 0 | **1** |
| 0 | 0 | 1 | 1 | 0 | 0 | **0** |
| 0 | 1 | 0 | 0 | 1 | 0 | **1** |
| 0 | 1 | 1 | 0 | 0 | 0 | **0** |
| 1 | 0 | 0 | 1 | 1 | 1 | **1** |
| 1 | 0 | 1 | 1 | 0 | 1 | **1** |
| 1 | 1 | 0 | 0 | 1 | 0 | **1** |
| 1 | 1 | 1 | 0 | 0 | 0 | **0** |
### **e) Duals of Boolean Expressions**
 1. Dual of x(y+1):
   
 2. Dual of \overline{x}\cdot0 + (\overline{y}+z):
   
### **f) Sum-of-Products (SOP) Expansion for F(x,y,z) = (x+y)\overline{z}**
 1. Expand expression:
   
 2. Insert missing variables:
   *    *  3. Combine terms:
   
### **g) Minimal Expansion using K-Map**
#### **Minterm Mapping**
#### **K-Map Diagram**
```text
       yz
  wx   00  01  11  10
  00 [  1   0   0   1  ]  -> m0, m2
  01 [  0   1   0   0  ]  -> m5
  11 [  1   0   0   0  ]  -> m12
  10 [  1   0   1   1  ]  -> m8, m11, m10

```
#### **Grouping & Simplification**
 1. **Quad (Four corners m_0, m_2, m_8, m_{10}):** \mathbf{\overline{x}\overline{z}}
 2. **Pair (m_8, m_{12}):** \mathbf{w\overline{y}\overline{z}}
 3. **Pair (m_{10}, m_{11}):** \mathbf{w\overline{x}y}
 4. **Single cell (m_5):** \mathbf{\overline{w}x\overline{y}z}
#### **Minimal Boolean Expression**
