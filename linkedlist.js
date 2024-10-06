class Node {
  constructor(data) {
    this.data = data;
    this.next = null;
  }
}

class LinkedList {
  constructor() {
    this.head = null;
  }

  // Insert at the end
  append(data) {
    let newNode = new Node(data);
    if (this.head === null) {
      this.head = newNode;
    } else {
      let current = this.head;
      while (current.next) {
        current = current.next;
      }
      current.next = newNode;
    }
  }

  // Print the list
  printList() {
    let current = this.head;
    while (current) {
      console.log(current.data);
      current = current.next;
    }
  }
}

// Example usage:
let list = new LinkedList();
list.append(10);
list.append(20);
list.append(30);
list.printList(); // Output: 10 20 30
