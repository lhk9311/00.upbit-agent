import axios from "axios";

export default axios.create({
  baseURL: "https://api.upbit.com/v1",
});