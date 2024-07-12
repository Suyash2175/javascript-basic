class Queue {
  constructor() {
    this.items = [];
  }

  enqueue(element) {
    this.items.push(element);
  }

  dequeue() {
    if (this.isEmpty()) {
      return "Underflow";
    }
    return this.items.shift();
  }

  front() {
    if (this.isEmpty()) {
      return "No elements in Queue";
    }
    return this.items[0];
  }

  isEmpty() {
    return this.items.length === 0;
  }

  printQueue() {
    return this.items.join(' ');
  }
}

// Example usage:
const queue = new Queue();
queue.enqueue(10);
queue.enqueue(20);
queue.enqueue(30);
console.log(queue.printQueue()); // Output: 10 20
